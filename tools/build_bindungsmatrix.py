#!/usr/bin/env python3
"""
tools/build_bindungsmatrix.py - Idempotenter Build-Layer auf v3-Daten.

Pipeline:
  v3.HEBEL_DETAIL + v3.MOD_API_ENTRIES + v3.ENGINE_API_ENTRIES
    -> AUDIT_RETAG (Dictionary, ID -> (Marker, Luecke))
    -> 3 MZ-Summary-Rows vor allen HEBEL-Zeilen
    -> sanitize(';' | \" | BOM | \\n) auf alle Felder
    -> atomic write: BINDUNGSMATRIX.csv.tmp + .bak-Backup des VORHERIGEN validen Stands

DATENSTRUKTUR-HINWEIS (CRITICAL für CSV-Konsumenten):
  Primary-Key in ID-Spalte ist NICHT unique. Jede HEBEL-ID kann MEHRERE Zeilen haben,
  eine pro Sub-Datenpunkt. Beispiel: `grep '^C8' BINDUNGSMATRIX.csv` returnt 2 Zeilen
  (SettTrade.tradeValue + Fiscal.settlePurchase). Konsumenten MÜSSEN Spalte 2 ("Datenpunkt")
  als Disambiguator nutzen oder Composite-Key (ID,Datenpunkt) bilden.
  
  Sub-Datenpunkt-Beispiele:
    A1 hat [price-array-Override, get-TRADABLE]
    B1 hat [credits, getD, inc, inc-mit-TRADABLE, inccc, CTYPE, update-inflation]
    B7 hat [people, totalMoney, incomeDue, incomePaid, workersUnpaid, meanWage,
           supplyPerDay, demandPerDay, stock, foodBasketPrice, wageShare]

Vorteil gg. direkter v3-Run:
- KEIN destruktiver os.remove() auf der Zieldatei vorher
- KEIN seen_slug.set()-Dedup, der Sub-Datenpunkte mit identischer ID schluckt
- Audit-Logik zentral dokumentiert (AUDIT_RETAG)
- Sanitize deckt ;, Quote-Chars, BOM und Newlines ab
- Smart .bak: backup NUR wenn Quell-Datei valid (NF=11 + Header match)
"""
import os
import sys
import shutil

# Daten aus v3 importieren (kein Re-Typing, single-source-of-truth)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_bindungsmatrix_v3 import (
    HEADER, OUT, HEBEL_DETAIL, MOD_API_ENTRIES, ENGINE_API_ENTRIES
)

ROOT = "/home/vannon/Schreibtisch/SyxEconomyMod_Workspace"

# ============================================================
# AUDIT-RETAG (User-Vorgabe 3 Makro-Befunde)
# ============================================================
# Marker-Wahl:
#   ++  = HEBEL+Mod bestaetigen sich
#   ??  = HEBEL-Claim korrekt, Mod hat keinen Ref (oder Mod-side missing)
#   ?   = unclear/vage
#   /   = Rebuttal -- HEBEL says X, Mod does Y (bewusst umgangen)
AUDIT_RETAG = {
    # F1-F7: Sektor-Tiefe existiert komplett NICHT im Mod-Code
    # ?: HEBEL says 'missing', Mod-Code confirms 'missing' -- ORTHOGONAL: weder
    #   ++ (was ein positives "implementiert+HEBEL+Mod-align" waere) noch / (HEBEL says X, Mod does Y)
    #   passt. Wir waehlen ?? als konservatives Signal: HEBEL-Claim ("existiert nicht")
    #   korrekt, Mod hat keinen Referenten. Wer wirklich 0 von 7 Sectors braucht,
    #   liest die Luecke.
    "F1": ("??", "ResourceRecipeIndex fehlt komplett im Mod-Code (verifiziert per Grep)"),
    "F2": ("??", "Topologische Sortierung fehlt komplett im Mod-Code"),
    "F3": ("??", "Branching-Faktor fehlt komplett im Mod-Code"),
    "F4": ("??", "Leontief-Inverse fehlt komplett im Mod-Code (baseValue aus priceXxxDefaults)"),
    "F5": ("??", "Sektor-Klassifikation RAW/PROCESSED/LUXURY fehlt komplett im Mod-Code"),
    "F6": ("??", "valueMultiplier pro Sektor fehlt komplett im Mod-Code (alle Faktor 1.0)"),
    "F7": ("??", "EconConfig.defaultScarcity fehlt komplett im Mod-Code (Hard Failure bei custom resource)"),
    # H8: HEBEL sagt phaseFactor EXISTIERT NICHT, Mod bestaetigt EXISTIERT NICHT --
    #   das ist gegenseitige Bestaetigung der ABSENZ. Per User-Spec ++ = "HEBEL und Mod
    #   bestaetigen sich" (auch wenn beide die Absenz bestaetigen). H8 ist KEIN Rebuttal
    #   im User-Spec-Sinn, weil HEBEL keine normative Erwartung formuliert.
    "H8": ("++", "phaseFactor Mod-side EXISTIERT NICHT -- HEBEL bestaetigt diese Absenz (mutual confirmation). Mod akzeptiert Preise-Clamp-Falle bei Pop<300 als Designentscheidung."),
    # K1-K5: StatsLaw lesbar, aber Mod hat eigene Ersatz-Mechanismen --
    #   HEBEL-Claim korrekt, kein Mod-Referent == ??
    "K1": ("??", "StatsLaw.guards ungenutzt -- Mod-Ersatz: CorveeController"),
    "K2": ("??", "StatsLaw.EQUALITY ungenutzt -- Mod-Ersatz: HealthPressure"),
    "K3": ("??", "StatsLaw.tyranny ungenutzt -- Mod-Ersatz: PovertyPressure"),
    "K4": ("??", "StatsLaw.lawMultiplier ungenutzt -- Produktivitaets-Potential ungenutzt"),
    "K5": ("??", "StatsLaw.escapees ungenutzt -- relevant nur fuer DebtBondage"),
    # C8: ECHTES Rebuttal -- HEBEL/TR-implementation sagt vanilla SettTrade.tradeValue-Coupling,
    #   Mod macht stattdessen Fiscal.settlePurchase (bewusste Pfadwahl). Beide Sub-Rows
    #   tragen denselben Marker, weil sie beide Aspekte DESSELBEN Rebuttals sind.
    "C8": ("/", "Rebuttal: Mod-Pfad Fiscal.settlePurchase ersetzt bewusst vanilla SettTrade.tradeValue-Coupling (Fiscal.java:99) -- Trade-Layer umgangen, Settlement-Layer aktiv"),
}

# ============================================================
# MZ SUMMARY (3 Makro-Befunde aus User-Rueckmeldung)
# ============================================================
MZ_SUMMARY = [
    (
        "MZ.1_SEKTOR-TIEFE",
        "Sektor-Tiefe fehlt komplett (F1-F7 EXISTIERT NICHT)",
        "ModArchitecture",
        "Mod-Code (101 core/.java)",
        "Recipe-Graph, Sektor-Klassifikation, Leontief",
        "static", "nein", "nein", "[!]",
        "Alle 7 F1-F7 HEBELs identifiziert -- Mod rechnet mit Angebot/Nachfrage OHNE Verstaendnis was aus was hergestellt wird (ResourceRecipeIndex, Topo-Sort, Branching, Leontief-Inverse, Sektor-Klassifikation RAW/PROCESSED/LUXURY, valueMultiplier, defaultScarcity). Konsequenz: Industry-Output wird als black-box in FlowMeter aggregiert -- Preise-zu-Sektor-Risiko nicht modelliert.",
        "??"
    ),
    (
        "MZ.2_BOOSTER-COVERAGE",
        "Booster-Coverage 3 von 402",
        "EngineAPI", "BOOSTABLES (Engine)", "Boostable-Verteilung",
        "static", "ja-3/402", "ja", "[x]",
        "Engine bietet 402 Verhaltens-Hebel. Mod nutzt nur LOYALTY (GiniConsequences.java), IMMIGRATION (MeticImmigration.register()) und GOV (EconProgression.adminBoostActive). Verbleibende 399 Booster als 'public final' in Engine vorhanden aber toter Code fuer den Mod. I8 BEHAVIOUR: 5 von 6 ungenutzt (LAWFULNESS/SUBMISSION/HAPPI/HAPPI_SLAVES/SANITY). I9 PHYSICS: 11 von 11 ignoriert. I10 NOBLE: 6 von 6 ungenutzt. I11 EQUIP: 32 Tool-Booster ohne Werkzeug-Produktivitaetskopplung.",
        "??"
    ),
    (
        "MZ.3_TRADE_LAW_LUECKEN",
        "Trade-System umgangen + StatsLaw ungenutzt",
        "ModArchitecture", "Mod-Code", "SettlementLayer + StatsLaw",
        "static", "nein", "nein", "[!]",
        "TradeManager/SettTrade/PBuyer/PSeller komplett umgangen -- Mod-Settlement via Fiscal.settlePurchase (C8 Rebuttal: Mod-Pfad ersetzt bewusst Engine-Trade-Layer). Trade-Toll laeuft parallel -- Doppelhandelskosten moeglich. StatsLaw lesbar (alle 5 HEBEL K1-K5 + K6 CRIMES public final) aber komplett ignoriert. Mod implementiert CorveeController/PovertyPressure/HealthPressure als Ersatz.",
        "??"
    ),
]


def sanitize(field):
    """Embedded ';' / "' / BOM / Newlines koennen CSV-Parser brechen.
    -> ';' wird ',', Quotes werden ' (verhindert csv.reader-Quote-Konflikte),
       BOM am Zeilenanfang wird gestrippt, Newlines werden Spaces."""
    if isinstance(field, str):
        s = field
        # BOM-Strip am Anfang
        if s.startswith("\ufeff"):
            s = s[1:]
        # Ersetze gefaehrliche Zeichen
        s = s.replace(";", ",")
        s = s.replace('"', "'")  # doppelte -> einzelne Quotes
        s = s.replace("\n", " ")
        s = s.replace("\r", " ")
        return s
    return field


def pad11(entry_id, fields):
    """Sichert NF=11: [ID + 10 Felder]. Padding mit LEERER String statt Em-Dash,
    damit Konsumenten 'pad' von 'inhalt' unterscheiden koennen."""
    row = [entry_id] + list(fields)
    while len(row) < 11:
        row.insert(len(row) - 1, "")  # leerer String, NICHT Em-Dash
    return row[:11] if len(row) > 11 else row


def build():
    """Sammelt alle Zeilen in der korrekten Reihenfolge."""
    rows = [HEADER]

    # 1. HEBEL_DETAIL - ALLE Eintraege behalten (kein seen_slug-Dedup)
    for entry in HEBEL_DETAIL:
        rows.append(pad11(entry[0], entry[1:]))

    # 2. MOD_API_ENTRIES (X.*)
    for entry in MOD_API_ENTRIES:
        rows.append(pad11(entry[0], entry[1:]))

    # 3. ENGINE_API_ENTRIES (Y.* prefix)
    for entry in ENGINE_API_ENTRIES:
        rows.append(pad11("Y." + entry[0], entry[1:]))

    # 4. AUDIT: ID -> (neuer Marker, neue Luecke)
    audit_ct = 0
    for row in rows[1:]:
        rid = row[0]
        if rid in AUDIT_RETAG:
            new_marker, new_luecke = AUDIT_RETAG[rid]
            row[10] = new_marker
            row[9] = new_luecke
            audit_ct += 1

    # 5. MZ-Summary-Rows direkt nach Header einfuegen
    mz_rows = [pad11(e[0], e[1:]) for e in MZ_SUMMARY]
    rows = [rows[0]] + mz_rows + rows[1:]

    # 6. Sanitize alle Felder (jedes ; durch , ersetzen)
    sanitize_ct = 0
    for row in rows[1:]:
        for i in range(len(row)):
            old = row[i]
            new = sanitize(old)
            if new != old:
                sanitize_ct += 1
            row[i] = new

    return rows, audit_ct, sanitize_ct


def safe_write(rows, target_path):
    """Atomic write: smart .bak + temp + replace.
    Backup NUR wenn die aktuelle Datei valid ist (NF=11 ueberall + Header match).
    Verhindert: .bak das selbst korrupt ist und Recovery nutzlos macht."""
    backup_path = target_path + ".bak"
    tmp_path = target_path + ".tmp"

    # Smart-Backup-Strategie: nur wenn Quell-Datei valid
    backup_msg = "(kein vorheriger Stand)"
    if os.path.exists(target_path):
        try:
            with open(target_path, "r", encoding="utf-8") as f:
                existing_lines = f.readlines()
            # Pruefe: mindestens 2 Zeilen (header + 1 data), Header passt, alle NF=11
            valid = len(existing_lines) >= 2
            if valid:
                valid = existing_lines[0].strip() == ";".join(HEADER)
            if valid:
                for ln in existing_lines[1:]:
                    if ln.strip() and len(ln.rstrip("\n").split(";")) != 11:
                        valid = False
                        break
            if valid:
                shutil.copy2(target_path, backup_path)
                backup_msg = f"{backup_path} (vorheriger valider Stand)"
            else:
                backup_msg = f"(kein Backup: vorheriger Stand war invalid -- NF-oder-Header-Mismatch)"
        except OSError as e:
            backup_msg = f"(kein Backup: {e})"

    # In Temp schreiben
    with open(tmp_path, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(";".join(row) + "\n")

    # Atomic rename -> ersetzt Zieldatei
    os.replace(tmp_path, target_path)

    return backup_msg


def validate(path):
    """Post-Write Validierung."""
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    nf_bad = []
    marker_dist = {}
    heb_ids = set()
    x_count = 0
    y_count = 0
    mz_count = 0
    headers_row = lines[0].strip()
    for nr, line in enumerate(lines[1:], 2):
        if not line.strip():
            continue
        fields = line.rstrip("\n").split(";")
        if len(fields) != 11:
            nf_bad.append((nr, len(fields), fields[0] if fields else "?"))
            continue
        marker_dist[fields[10]] = marker_dist.get(fields[10], 0) + 1
        rid = fields[0]
        if rid.startswith("X."):
            x_count += 1
        elif rid.startswith("Y."):
            y_count += 1
        elif rid.startswith("MZ."):
            mz_count += 1
        else:
            heb_ids.add(rid.split(".")[0])  # A1 == A1.foo == A1.bar

    return {
        "total_lines": len(lines),
        "data_lines": len(lines) - 1,
        "nf_bad": nf_bad,
        "marker_dist": marker_dist,
        "heb_count": len(heb_ids),
        "x_count": x_count,
        "y_count": y_count,
        "mz_count": mz_count,
    }


def main():
    rows, audit_ct, sanitize_ct = build()
    backup_msg = safe_write(rows, OUT)
    stats = validate(OUT)

    print(f"=== BUILD SUCCESS ===")
    print(f"Total Zeilen:     {stats['total_lines']} (incl. Header)")
    print(f"Datenzeilen:      {stats['data_lines']}")
    print(f"Audit angewandt:  {audit_ct} Zeilen")
    print(f"Field-Sanitizes:  {sanitize_ct}")
    print(f"Backup:           {backup_msg}")
    print(f"")
    print(f"NF!=11 Zeilen:    {stats['nf_bad']} (leer = perfekt)")
    print(f"Marker-Distribution (User-strikt 4-Spec ++|??|?|/):")
    for marker in ("++", "??", "?", "/"):
        cnt = stats['marker_dist'].get(marker, 0)
        print(f"  {marker:>4} = {cnt}")
    # Andere/unbekannte Marker anzeigen
    other = {k: v for k, v in stats['marker_dist'].items() if k not in ("++", "??", "?", "/")}
    if other:
        print(f"  other: {other}")
    print(f"")
    print(f"HEBEL-IDs distinct: {stats['heb_count']}  (Ziel: 129)")
    print(f"Mod-API  (X.*):     {stats['x_count']}")
    print(f"Engine-API (Y.*):   {stats['y_count']}")
    print(f"MZ-Summary (MZ.*):  {stats['mz_count']}")


if __name__ == "__main__":
    main()
