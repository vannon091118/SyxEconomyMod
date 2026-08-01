#!/usr/bin/env python3
"""
tools/build_bindungsmatrix.py — CSV SSOT Validate + Audit + Sanitize Tool.

Liest BINDUNGSMATRIX.csv als Single-Source-of-Truth.
Wendet AUDIT_RETAG-Marker auf spezifizierte IDs an,
sanitized Embedded-Semicolons/Quotes/BOM/Newlines,
validiert NF=11 auf allen Zeilen,
schreibt atomar mit .bak-Backup (nur wenn Quell-Datei valid war).

Pipeline:
  CSV lesen → AUDIT_RETAG anwenden → Felder sanitizen → validieren → atomar schreiben

KEIN Import von gen_bindungsmatrix_v3.py (geloescht seit v0.13.x).
Die CSV selbst ist der kanonische Stand — dieses Script ist der
Rebuild-Layer fuer Sanitize + NF-Pruefung + Audit-Marker.

DATENSTRUKTUR-HINWEIS (CRITICAL fuer CSV-Konsumenten):
  Primary-Key in ID-Spalte ist NICHT unique. Jede HEBEL-ID kann MEHRERE Zeilen haben,
  eine pro Sub-Datenpunkt. Beispiel: `grep '^C8' BINDUNGSMATRIX.csv` returnt 2 Zeilen
  (SettTrade.tradeValue + Fiscal.settlePurchase). Konsumenten MUESSEN Spalte 2 ("Datenpunkt")
  als Disambiguator nutzen oder Composite-Key (ID,Datenpunkt) bilden.
"""

import os
import sys
import shutil

ROOT = "/home/vannon/Schreibtisch/SyxEconomyMod_Workspace"
CSV_PATH = os.path.join(ROOT, "BINDUNGSMATRIX.csv")

HEADER = [
    "ID", "Datenpunkt", "Wert-Typ", "Quelle-Klasse", "Zugriffspfad",
    "Zugriffsart", "Mod nutzt", "UI-Kandidat", "Status", "Lücke", "ModVerifiziert"
]

# ============================================================
# AUDIT-RETAG — 4 semantische Luecken (nicht NF-Fehler, Audit-Befunde)
# ============================================================
# Marker:
#   ++  = HEBEL+Mod bestaetigen sich (auch mutual absence)
#   ??  = HEBEL-Claim korrekt, Mod hat keinen Ref (oder Mod-side missing)
#   ?   = unclear/vage
#   /   = Rebuttal — HEBEL says X, Mod does Y (bewusst umgangen)
#
# Jeder Eintrag: (neuer_Marker, neue_Luecke)
# Beide Felder werden in der CSV UEBERSCHRIEBEN — die CSV IST die Truth,
# AUDIT_RETAG ist der dokumentierte Override-Layer fuer die 4 Luecken.
AUDIT_RETAG = {
    # Lücke 1: Sektor-Tiefe (F1-F7) → ??
    # HEBEL sagt 'missing', Mod-Code bestaetigt 'missing'.
    # ?? = konservativ: HEBEL-Claim korrekt, Mod hat keinen Referenten.
    "F1": ("??", "ResourceRecipeIndex fehlt komplett im Mod-Code (verifiziert per Grep)"),
    "F2": ("??", "Topologische Sortierung fehlt komplett im Mod-Code"),
    "F3": ("??", "Branching-Faktor fehlt komplett im Mod-Code"),
    "F4": ("??", "Leontief-Inverse fehlt komplett im Mod-Code (baseValue aus priceXxxDefaults)"),
    "F5": ("??", "Sektor-Klassifikation RAW/PROCESSED/LUXURY fehlt komplett im Mod-Code"),
    "F6": ("??", "valueMultiplier pro Sektor fehlt komplett im Mod-Code (alle Faktor 1.0)"),
    "F7": ("??", "EconConfig.defaultScarcity fehlt komplett im Mod-Code (Hard Failure bei custom resource)"),
    # Lücke 2: phaseFactor (H8) → ++
    # HEBEL sagt 'EXISTIERT NICHT', Mod bestaetigt 'EXISTIERT NICHT'.
    # ++ = mutual confirmation der Absenz (kein Rebuttal).
    "H8": ("++", "phaseFactor Mod-side EXISTIERT NICHT -- HEBEL bestaetigt diese Absenz (mutual confirmation). Mod akzeptiert Preise-Clamp-Falle bei Pop<300 als Designentscheidung."),
    # Lücke 3: StatsLaw (K1-K5) → ??
    # StatsLaw lesbar, Mod hat eigene Ersatz-Mechanismen.
    "K1": ("??", "StatsLaw.guards ungenutzt -- Mod-Ersatz: CorveeController"),
    "K2": ("??", "StatsLaw.EQUALITY ungenutzt -- Mod-Ersatz: HealthPressure"),
    "K3": ("??", "StatsLaw.tyranny ungenutzt -- Mod-Ersatz: PovertyPressure"),
    "K4": ("??", "StatsLaw.lawMultiplier ungenutzt -- Produktivitaets-Potential ungenutzt"),
    "K5": ("??", "StatsLaw.escapees ungenutzt -- relevant nur fuer DebtBondage"),
    # Lücke 4: C8 SettTrade → /
    # Echtes Rebuttal: HEBEL sagt vanilla SettTrade.tradeValue-Coupling,
    # Mod macht stattdessen Fiscal.settlePurchase (bewusste Pfadwahl).
    # Beide Sub-Rows tragen denselben Marker.
    "C8": ("/", "Rebuttal: Mod-Pfad Fiscal.settlePurchase ersetzt bewusst vanilla SettTrade.tradeValue-Coupling (Fiscal.java:99) -- Trade-Layer umgangen, Settlement-Layer aktiv"),
}


def sanitize(field):
    """Embedded ';' / '\"' / BOM / Newlines koennen CSV-Parser brechen.
    -> ';' wird ',', Quotes werden ', BOM am Zeilenanfang wird gestrippt,
       Newlines werden Spaces. str.split() produziert nur strings, nie None."""
    if not isinstance(field, str):
        return str(field)
    s = field
    if s.startswith("\ufeff"):
        s = s[1:]
    s = s.replace(";", ",")
    s = s.replace('"', "'")
    s = s.replace("\n", " ")
    s = s.replace("\r", " ")
    return s


def read_csv(path):
    """Liest CSV als Liste von Listen. Erste Zeile = Header."""
    rows = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n").rstrip("\r")
            if not line.strip():
                continue  # ueberspringe Leerzeilen
            fields = line.split(";")
            rows.append(fields)
    return rows


def apply_audit(rows):
    """Ueberschreibt Luecke (Index 9) und ModVerifiziert (Index 10)
    fuer alle Zeilen deren ID in AUDIT_RETAG ist.
    Gibt Anzahl ueberschriebener Zeilen zurueck."""
    audit_ct = 0
    for row in rows[1:]:  # Header ueberspringen
        if len(row) >= 11:
            rid = row[0].strip()
            if rid in AUDIT_RETAG:
                new_marker, new_luecke = AUDIT_RETAG[rid]
                row[9] = new_luecke
                row[10] = new_marker
                audit_ct += 1
    return audit_ct


def sanitize_rows(rows):
    """Sanitized alle Felder in allen Zeilen (ausser Header).
    Gibt Anzahl sanitizierter Felder zurueck."""
    sanitize_ct = 0
    for row in rows[1:]:
        for i in range(len(row)):
            old = row[i]
            new = sanitize(old)
            if new != old:
                sanitize_ct += 1
            row[i] = new
    return sanitize_ct


def validate(rows):
    """Validiert die CSV-Struktur.
    Returns dict mit total_lines, nf_bad, marker_dist, x_count, y_count, mz_count."""
    nf_bad = []
    marker_dist = {}
    x_count = 0
    y_count = 0
    mz_count = 0
    empty_luecke = 0

    for nr, row in enumerate(rows[1:], 2):
        if len(row) != 11:
            nf_bad.append((nr, len(row), row[0] if row else "?"))
            continue
        marker = row[10].strip()
        marker_dist[marker] = marker_dist.get(marker, 0) + 1
        rid = row[0].strip()
        if rid.startswith("X."):
            x_count += 1
        elif rid.startswith("Y."):
            y_count += 1
        elif rid.startswith("MZ."):
            mz_count += 1
        if row[9].strip() == "":
            empty_luecke += 1

    return {
        "total_lines": len(rows),
        "data_lines": len(rows) - 1,
        "nf_bad": nf_bad,
        "marker_dist": marker_dist,
        "x_count": x_count,
        "y_count": y_count,
        "mz_count": mz_count,
        "empty_luecke": empty_luecke,
    }


def safe_write(rows, target_path):
    """Atomic write: smart .bak + temp + replace.
    Backup NUR wenn die aktuelle Datei valid ist (NF=11 ueberall + Header match)."""
    backup_path = target_path + ".bak"
    tmp_path = target_path + ".tmp"

    backup_msg = "(kein vorheriger Stand)"
    if os.path.exists(target_path):
        try:
            existing = read_csv(target_path)
            valid = len(existing) >= 2
            if valid:
                valid = ";".join(existing[0]).strip() == ";".join(HEADER)
            if valid:
                for row in existing[1:]:
                    if len(row) != 11:
                        valid = False
                        break
            if valid:
                shutil.copy2(target_path, backup_path)
                backup_msg = f"{backup_path} (vorheriger valider Stand)"
            else:
                backup_msg = "(kein Backup: vorheriger Stand war invalid -- NF-oder-Header-Mismatch)"
        except OSError as e:
            backup_msg = f"(kein Backup: {e})"

    with open(tmp_path, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(";".join(row) + "\n")

    os.replace(tmp_path, target_path)
    return backup_msg


def main():
    # 1. CSV lesen + Header validieren
    rows = read_csv(CSV_PATH)
    if ";".join(rows[0]).strip() != ";".join(HEADER):
        print("ERROR: CSV header mismatch — erwartet:", ";".join(HEADER), file=sys.stderr)
        print("       gelesen:", ";".join(rows[0]).strip(), file=sys.stderr)
        sys.exit(1)
    print(f"CSV gelesen: {len(rows)} Zeilen (incl. Header)")

    # 2. AUDIT_RETAG anwenden
    audit_ct = apply_audit(rows)
    print(f"Audit angewandt: {audit_ct} Zeilen")

    # 3. Sanitize
    sanitize_ct = sanitize_rows(rows)
    print(f"Field-Sanitizes: {sanitize_ct}")

    # 4. Atomar schreiben
    backup_msg = safe_write(rows, CSV_PATH)
    print(f"Backup: {backup_msg}")

    # 5. Validieren (nach Schreiben: Lese frisch ein)
    rows_validated = read_csv(CSV_PATH)
    stats = validate(rows_validated)

    print()
    print("=== VALIDATE ===")
    print(f"Total Zeilen:     {stats['total_lines']} (incl. Header)")
    print(f"Datenzeilen:      {stats['data_lines']}")
    print(f"NF!=11 Zeilen:    {stats['nf_bad']} (leer = perfekt)")
    print(f"Leere Luecke:     {stats['empty_luecke']} (Ziel: 0)")
    print()
    print("Marker-Distribution:")
    for marker in ("++", "??", "?", "/"):
        cnt = stats["marker_dist"].get(marker, 0)
        print(f"  {marker:>4} = {cnt}")
    other = {k: v for k, v in stats["marker_dist"].items() if k not in ("++", "??", "?", "/")}
    if other:
        print(f"  other: {other}")
    print()
    print(f"X-Mod rows:       {stats['x_count']}")
    print(f"Y-Engine rows:    {stats['y_count']}")
    print(f"MZ-Summary rows:  {stats['mz_count']}")

    # Exit-Code
    if stats["nf_bad"] or stats["empty_luecke"] > 0:
        print()
        print("=== VALIDATE FAILED ===")
        print(f"Backup verfuegbar: {CSV_PATH}.bak")
        sys.exit(1)
    else:
        print()
        print("=== VALIDATE PASSED ===")


if __name__ == "__main__":
    main()
