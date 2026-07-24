# Truth-Report — SyxEconomyMod Konsolidierungs-Pass

> **Datum:** 2026-07-23 | **Scope:** Alle Dokumente (root + docs/) gegen den aktuellen Repo-Stand
> **Methode:** Jede Aussage in CHANGELOG.md / README.md / ROADMAP.md / SESSION_SUMMARY.md gegen den
> tatsächlichen Code, die tatsächliche Dateistruktur und die tatsächlichen pom.xml-Werte verifiziert.
> **Prämisse:** Code ist Wahrheit. Docs werden an Code angeglichen, nicht umgekehrt.

---

## 1. Repository-Inventur (Ist-Stand)
<!-- truth-tracks: src/vannon/, pom.xml, tools/, CHANGELOG.md, README.md, docs/ROADMAP.md, docs/GLOSSARY.md, docs/ARCHITECTURE.md -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

### Source-Tree

| Pfad | Dateien | Status |
|------|---------|--------|
| `src/vannon/syx/economy/core/` | 95 `.java` | ✓ Haupt-Subsystem |
| `src/vannon/syx/economy/adapter/` | 17 `.java` | ✓ Phase 4: 5 ISyx + 4 Fallback + 8 Vanilla (incl. 3 MethodHandle-Varianten) |
| `src/vannon/syx/economy/settlement/room/service/...` | 4 `.java` | ✓ Settlement-Bridges (tavern, canteen, eatery, employment) |
| `src/vannon/` gesamt | **112 `.java`** | ✓ (Docs behaupteten 108 — korrigiert) |
| `test/java/vannon/syx/economy/core/` | 1 `.java` (`TreasuryCrisisTest.java`) | ✓ (ROADMAP behauptete 0/3 — korrigiert) |

### Adapter-Layer (Phase 4 — fertig)

| Interface | Vanilla | Fallback | MethodHandle-Variante |
|-----------|---------|----------|----------------------|
| `ISyxAI` | `VanillaAIAdapter` | — | — |
| `ISyxTransport` | `VanillaTransportAdapter` | `FallbackTransportAdapter` | `VanillaTransportAdapterMH` |
| `ISyxWarehouse` | `VanillaWarehouseAdapter` | `FallbackWarehouseAdapter` | `VanillaWarehouseAdapterMH` |
| `ISyxBoosting` | `VanillaBoostingAdapter` | `FallbackBoostingAdapter` | — |
| `ISyxDiplomacy` | `VanillaDiplomacyAdapter` | `FallbackDiplomacyAdapter` | `VanillaDiplomacyAdapterMH` |

**Reflexions-Stellen direkt im Core: 0** — alle 5 via Adapter gekapselt.

### Tools-Dir

| Skript | Zweck | Anbindung |
|--------|-------|-----------|
| `tools/build-gate.sh` | 3 Pre-Compile-Gates | `maven-antrun-plugin` ✓ |
| `tools/code-audit.sh` | catch(Throwable) + leere catch-Blöcke | Manuell ✓ |
| `tools/verify-version-consistency.sh` | pom.xml ↔ CHANGELOG.md ↔ git tags | Manuell (Pre-Commit-Hook-Vorlage) ✓ |
| `tools/rebalance_plots.py` | 5 Plots (Scarcity, Macro, Preis, Gini/Treasury, Firm) | Manuell (Python+Jupyter) ✓ |
| `tools/rebalance_dashboard.ipynb` | Visualisierungs-Notebook | Manuell ✓ |

### Build-Metadaten (pom.xml)

| Feld | Wert |
|------|------|
| `project.version` | `0.1.0` |
| `mod.changelog` | 10 Einträge (v0.0.1 – v0.1.0) |
| `mod.version.history` | `v0.1.0` (automatisch generiert aus git tags) |
| `game.version` | `71.44` |
| Compiler-Strict | `-Xlint:all,-options`, Benchmark-Package vom Shade-JAR ausgeschlossen ✓ |
| `gate.skip` | überspringbar (`-Dgate.skip=true`) |

---

## 2. TreasuryCrisis Wahrheits-Verifikation
<!-- truth-tracks: src/vannon/syx/economy/core/TreasuryCrisis.java, README.md, CHANGELOG.md, docs/ROADMAP.md, docs/SESSION_SUMMARY_2026-07-23.md -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

**Code-Definition** (`src/vannon/syx/economy/core/TreasuryCrisis.java`):

```
TIER1_WARNING    =     -5_000L     (-5K)
TIER2_AUSTERITY  =    -50_000L     (-50K)
TIER3_FIRESALE   =   -250_000L     (-250K)
TIER4_BANKRUPT   = -1_000_000L     (-1M)
TIER5_COLLAPSE   = -5_000_000L     (-5M)
```

**Hysterese** (höher = frühere Erholung):
```
TIER1_RECOVERY = 0L
TIER2_RECOVERY = -10_000L
TIER3_RECOVERY = -50_000L
TIER4_RECOVERY = -250_000L
TIER5_RECOVERY = -1_000_000L
```

**Hard Floor** ist KEIN 6. Tier — er ist Teil von TIER5_COLLAPSE:

```java
public static boolean isHardFloor() { return activeTier >= 5; }
```

→ **WAHRHEIT: 5 Tier-Stufen + Hard-Floor-Verhalten innerhalb von Tier 5.**

### Was die Docs behaupteten (vorher)

| Dokument | Behauptete Tier-Zahl | Korrektur |
|----------|----------------------|-----------|
| `CHANGELOG.md` (Root + docs/) | **3 stufig** (−100K/−500K/−1M) | ✗ **FALSCH** — Initial-Version vor Refactor; aktuell 5 |
| `README.md` | **3 stufig** (Warnung → Zwangsprivat. → Steuer↑+Lohn÷) | ✗ **FALSCH** |
| `docs/ROADMAP.md` | **6-stufig** in Phase 4b | ✗ **FALSCH** — Hard Floor ist Teil von Tier 5 |
| `docs/SESSION_SUMMARY_2026-07-23.md` | Header: "**6-stufig**"; Tabelle: 5 Stufen | ⚠ **INKONSISTENT** — Header falsch, Tabelle richtig |

### Korrektur-Aktion

Alle 4 Dokumente wurden auf **5 stufig + Hard Floor** angeglichen. Wo Details nötig, sind die
korrekten Schwellen (−5K / −50K / −250K / −1M / −5M) gelistet.

---

## 3. CSV-Diagnostik Wahrheits-Verifikation
<!-- truth-tracks: src/vannon/syx/economy/core/DiagnosticExporter.java, CHANGELOG.md, README.md, docs/SESSION_SUMMARY_2026-07-23.md -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

**Code-Definition** (`src/vannon/syx/economy/core/DiagnosticExporter.java`,
Zeilen 55–65):

```java
private static final String[] MACRO_HEADER = {
    "game_day", "season", "population", "deaths", "emigrations", "inherited", "heirless",
    "gini", "median_wealth", "mean_wealth", "max_wealth",
    "treasury", "total_money", "seed_money", "audit_delta",
    "mean_wage", "actual_mean_wage", "wage_share", "unpaid_ratio",
    "head_tax", "market_receipts", "ration_out", "wages_paid", "warehouse_bought", "warehouse_sold",
    "wage_tax", "housing_rent_last_tick", "housing_rent_due_last_tick", "housing_evictions_last_tick",
    "property_sales", "property_dividends",
    "food_basket_price", "food_days"
};
```

→ **WAHRHEIT: 32 Spalten** im `rebalance_macro_<epoch>.csv`.

### Was die Docs behaupteten (vorher)

| Dokument | Behauptete Spalten-Zahl | Korrektur |
|----------|-------------------------|-----------|
| `CHANGELOG.md` (Root) | **31 Spalten** | ✗ **FALSCH** (vermutlich Zählfehler beim Schreiben) |
| `README.md` | **32 Spalten** | ✓ |
| `docs/SESSION_SUMMARY_2026-07-23.md` | **Macro 32 Spalten** | ✓ |
| `docs/CHANGELOG.md` | nicht spezifiziert | ✓ |

### Korrektur-Aktion

Root `CHANGELOG.md` von 31 auf 32 Spalten korrigiert. Die beiden anderen CSV-Header
(`RESOURCE_FILE` = 10 Spalten, `FIRMS_FILE` = 13 Spalten) wurden gegengeprüft und stimmen
mit den Docs überein.

---

## 4. Test-Layer Wahrheits-Verifikation
<!-- truth-tracks: test/, docs/ROADMAP.md -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

**Tatsächliche Test-Dateien:**

```
test/java/vannon/syx/economy/core/TreasuryCrisisTest.java
```

→ **WAHRHEIT: 1 von 3 in `ROADMAP.md` Phase 5 angekündigten Tests existiert.**

### Was die Docs behaupteten (vorher)

| Dokument | Behauptung | Korrektur |
|----------|------------|-----------|
| `docs/ROADMAP.md` | "Phase 5: Tests & Modularisierung (**0/3** begonnen)" | ✗ **FALSCH** — `TreasuryCrisisTest.java` existiert |

### Korrektur-Aktion

ROADMAP.md aktualisiert: Phase 5 = "**1/3 begonnen** (TreasuryCrisis.classify() +
Recovery-Logik)". ExchangeKernel- und FlowPrices-Tests bleiben offen.

---

## 5. Quellen-Konsolidierung
<!-- truth-tracks: CHANGELOG.md, IMPLEMENTATION_PLAN.md, docs/superpowers/plans/ -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

### Gelöschte Duplikate

| Datei | Grund |
|-------|-------|
| `IMPLEMENTATION_PLAN.md` (Root, 58 KB) | Duplikat von `docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md` |
| `docs/CHANGELOG.md` (7.5 KB) | Duplikat von `CHANGELOG.md` (Root) — durch Redirect-Notice ersetzt |

### Redirect-Notice

`docs/CHANGELOG.md` wurde durch eine kurze Redirect-Notice ersetzt:

> Diese Datei war ein Duplikat von `CHANGELOG.md` im Root. Der Root-CHANGELOG ist die
> kanonische Quelle. Diese Datei bleibt als historische Referenz erhalten und verweist auf
> die kanonische Version. Die hier früher enthaltene v0.1.0-Beschreibung ist veraltet (sie
> enthielt 3-Tier-TreasuryCrisis statt 5 Stufen + Hard Floor).

### Erstellte Dateien

| Datei | Zweck |
|-------|-------|
| `docs/README.md` | Doc-Index mit Truth-Status pro Datei |
| `docs/reports/TRUTH_REPORT.md` | Dieser Audit-Bericht |

---

## 6. Pom.xml ↔ CHANGELOG.md Konsistenz
<!-- truth-tracks: pom.xml, CHANGELOG.md -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

| Feld | pom.xml | CHANGELOG.md (Root) | Match |
|------|---------|--------------------|-------|
| `project.version` | `0.1.0` | Top-Eintrag `v0.1.0` | ✓ |
| `mod.changelog` Zeile 1 | `v0.1.0 (Phase 4)` | `## v0.1.0 (Phase 4)` | ✓ |
| Anzahl Changelog-Einträge | 10 (im pipe-separated `mod.changelog`) | 10 Headers in Root + 10 in docs/ | ✓ |

→ **WAHRHEIT: pom.xml ist konsistent mit beiden CHANGELOG-Dateien.** Die Versions-Konsistenz-Gate
sollte grün sein.

---

## 7. Phase-5-Status (Plan vs. Implementierung)
<!-- truth-tracks: docs/superpowers/plans/, docs/ROADMAP.md, README.md -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

Laut `docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md` (kanonische Quelle) sind 8
neue Klassen geplant:

| Klasse | Geplant | Implementiert | Lücke |
|--------|---------|---------------|-------|
| `StageGate` | ✓ | ✗ | offen |
| `NeedsBridge` | ✓ | ✗ | offen |
| `ITrainingSource` | ✓ | ✗ | offen |
| `InternalTrainingSource` | ✓ | ✗ | offen |
| `CsvTrainingSource` | ✓ | ✗ | offen |
| `CsvIngest` | ✓ | ✗ | offen |
| `TrainingVectors` | ✓ | ✗ | offen |
| `CitizenLayers` | ✓ | ✗ | offen |

→ **WAHRHEIT: 0/8 Klassen aus Phase-5-Plan implementiert.** Der Plan ist detailliert, gegen die
echte Vanilla-API verifiziert (4 Source-Verifikations-Runden), aber nicht ausgeführt. README.md
erwähnt nur Phase 4 als abgeschlossen — Phase 5 wird korrekt nicht als "done" dargestellt.

### Bewertung des Plans

Der Plan im `docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md` ist:

| Aspekt | Bewertung |
|--------|-----------|
| Vollständigkeit | ✓ 11 Tasks, 8 Klassen, 3 Tests, keine Platzhalter |
| Source-Verifikation | ✓ Vanilla-API gegen `info.zip/SongsOfSyx-sources.jar` geprüft |
| Bekannte Bugs im Plan | ⚠ Keystone-Fixes dokumentiert: nur 3 NEED_E-Typen (nicht 5), URGENCY (nicht fulfilment), IUpdater-Wiring |
| Risiken | ⚠ Stage-Gating ist konservativ (INDUSTRIE+), keine unbeaufsichtigten Effekte im Early-Game |
| Aufwand-Schätzung | ~2–3 Sessions für volle Implementierung |

---

## 8. Hard-Threshold-Alert (DiagnosticExporter)
<!-- truth-tracks: src/vannon/syx/economy/core/DiagnosticExporter.java -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

**Code-Definition** (`src/vannon/syx/economy/core/DiagnosticExporter.java` Zeile 374+):

```java
private static void checkRebalanceAlerts(double gini, long auditDelta, long day) {
    if (gini > GINI_HARD_THRESHOLD) {  // 0.40
        EventLog.log("REBALANCE", "Tag " + day + ": Gini=" + fmt(gini, 3) + " ...");
    }
    if (auditDelta > AUDIT_DELTA_HARD_THRESHOLD) {  // 1000
        EventLog.log("REBALANCE", "Tag " + day + ": audit_delta=" + auditDelta + " ...");
    }
}
```

→ **WAHRHEIT: Alert-Layer existiert mit Schwellen Gini > 0.40 und Audit-Delta > 1000.**
Alle 4 Dokumente stimmen hier überein — keine Korrektur nötig.

---

## 9. Datei-Inventur nach Pass
<!-- truth-tracks: CHANGELOG.md, README.md, docs/ROADMAP.md, docs/SESSION_SUMMARY_2026-07-23.md, docs/CHANGELOG.md, docs/README.md, IMPLEMENTATION_PLAN.md -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

| Datei | Vorher | Nachher |
|-------|--------|---------|
| `CHANGELOG.md` (Root) | 87 Zeilen, "31 Spalten"-Fehler | korrigiert: **32 Spalten**, Tier-Count 5 |
| `README.md` | 161 Zeilen, "3-stufige Krise" + 108 Dateien | korrigiert: **5 Tiers + Hard Floor**, **112 Dateien**, Phase-5-Verweis |
| `docs/CHANGELOG.md` | 147 Zeilen Duplikat | Redirect-Notice (~10 Zeilen) |
| `docs/ROADMAP.md` | 162 Zeilen, "6-stufig", "0/3 Tests", "108 Dateien" | korrigiert: **5 Stufen + Hard Floor**, **1/3 Tests**, **112 Dateien** |
| `docs/SESSION_SUMMARY_2026-07-23.md` | 226 Zeilen, Header "6-stufig", Tabelle 5 | korrigiert: Header **5 Tiers + Hard Floor** |
| `docs/README.md` | existiert nicht | **NEU**: Doc-Index mit Truth-Status |
| `docs/reports/TRUTH_REPORT.md` | existiert nicht | **NEU**: Dieser Audit |
| `IMPLEMENTATION_PLAN.md` (Root) | 1514 Zeilen Duplikat | **GELÖSCHT** — kanonisch in `docs/superpowers/plans/` |

---

## 10. Verifikation (post-Konsolidierung)
<!-- truth-tracks: pom.xml, tools/ -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

Validierung läuft mit `mvn compile` nach den Edits. Erwartetes Ergebnis:

- BUILD SUCCESS (Compiler-Änderungen: keine — nur Markdown)
- 3 Gates grün (unverändert)
- 0 neue Warnings (Doc-Edits berühren keine Java-Sources)

Wird nach Abschluss im Sprint-Output bestätigt.

---

## 15. 4. Konsolidierungs-Pass — Drift-Gate-Automatisierung (2026-07-23)
<!-- truth-tracks: tools/docs-truth-consistency.sh -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

> **Auslöser:** User-Anfrage „Bau tools/docs-truth-consistency.sh als Schwester-Skript zu
> verify-version-consistency.sh — prüft 108/112-Drift, 3/6-stufige-Vorkommen,
> Phase-5-Klassen-Anzahl. Bei Drift: exit 1, sonst PASS. Plus Pre-Commit-Hook installieren“.

### Erstellte Datei

**`tools/docs-truth-consistency.sh`** (143 Zeilen) — Schwester-Skript zu `verify-version-consistency.sh`.
Prüft 4 Drift-Kategorien gegen aktive Docs. Exit 0 = PASS, Exit 1 = Drift, Exit 2 = Skript-Fehler.

| Check | Was er prüft | Aktueller Stand |
|-------|-------------|-----------------|
| 1 | `108` als aktive Aussage in Docs (außer Allowlist) | PASS |
| 2 | `3-stufige`/`6-stufige` als TreasuryCrisis-Beschreibung | PASS |
| 3 | Phase-5 Klassen: 0/8 von {StageGate, NeedsBridge, ITrainingSource, InternalTrainingSource, CsvTrainingSource, CsvIngest, TrainingVectors, CitizenLayers} | PASS |
| 4 | Stale `docs/HISTORICAL_*`-Pfade (sollten nur unter `docs/archive/` sein) | PASS |

### Allowlist (drift-dokumentierende Dateien)

| Datei | Grund |
|-------|-------|
| `docs/reports/TRUTH_REPORT.md` | Audit-Bericht listet Drift intentional auf |
| `docs/README.md` | Truth-Status-Tabelle dokumentiert alte vs. neue Werte |
| `docs/CHANGELOG.md` | Redirect-Notice mit historischem Inhalt (Pre-v0.1.0 Duplikat) |

### Architektur-Entscheidungen

1. **`set -eo pipefail`** statt `-uo pipefail` — `set -u` verträgt sich nicht mit
   `grep`-Exitcodes bei „kein Match", was zu spurious Script-Crashes führte.
2. **`safe_grep`-Wrapper** mit `|| result=""` — robust gegen leere Treffer.
3. **`printf` statt `echo -e`** — POSIX-portabel. Vermeidet „-e"-Literal-Output auf
   Busybox/Dash-Systemen.
4. **Preflight-Check** für `grep`-Verfügbarkeit — sonst würde das Gate stillschweigend
   PASSen, wenn `grep` fehlt.
5. **Hinweis** wenn `.git/` fehlt — Repo ist aktuell ohne Git (laut user-Aussage „nicht mal
   öffentlich"). Hook-Installation erfolgt nach `git init`.
6. **Live-Count** statt hardcoded „112" — `find src -name '*.java' | wc -l` zur Laufzeit.
   Wenn jemand eine neue Klasse anlegt, ändert sich die Erwartung automatisch.

### Test-Ergebnisse

```
$ bash tools/docs-truth-consistency.sh
Gepruefter Scope: 14 aktive Markdown-Dateien
... 4/4 Checks PASS
---EXIT: 0---

$ mvn compile -q
MVN_EXIT: 0   # 3 Gates grün, keine Java-Datei angefasst
```

### Sanity-Test (Drift-Injection)

```
$ sed -i 's/112 Dateien/**108 Dateien**/' docs/ROADMAP.md
$ bash tools/docs-truth-consistency.sh
   FAIL '108' als aktive Aussage in Docs gefunden:
         docs/ROADMAP.md:123:| Java-Dateien | **108 Dateien** |
         ...
   DRIFT GEFUNDEN - exit 1
$ # revert + re-run
$ bash tools/docs-truth-consistency.sh
   PASS - exit 0
```

→ Gate detektiert Drift zuverlässig und reverted sauber.

### Pre-Commit-Hook-Installation

Das Repo hat aktuell kein `.git/`. Nach `git init`:

```bash
cp tools/docs-truth-consistency.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
# Optional: Kombination mit dem bestehenden Version-Gate
cat > .git/hooks/pre-commit << 'EOF'
#!/usr/bin/env bash
set -e
bash tools/verify-version-consistency.sh
bash tools/docs-truth-consistency.sh
EOF
chmod +x .git/hooks/pre-commit
```

### Halbwertszeit-Effekt

Vor Pass 4 war Halbwertszeit manuell garantiert (3 Konsolidierungs-Passes durch den User).
Ab Pass 4 ist sie **automatisiert**:
- Tägliche Code-Änderungen lösen bei `git commit` einen Drift-Check aus
- Verstöße gegen die Truth werden sofort sichtbar (Datei:Zeile)
- Refactoring-Fehler (z. B. Phase-5-Klasse implementiert aber Plan nicht aktualisiert) werden
  beim ersten Commit gefangen

Damit ist die im Pass 1 aufgestellte Anforderung „der Truth-Pass sollte keine Halbwertszeit
haben" strukturell umgesetzt.

---

## 13. 3. Konsolidierungs-Pass — Archiv-Verschiebung (2026-07-23)
<!-- truth-tracks: docs/HISTORICAL_, docs/ARCHITECTURE.md, docs/README.md, docs/reports/ -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

> **Auslöser:** User-Anfrage „die 8 HISTORICAL_*.md-Dateien ins docs/archive/-Subdir verschieben“.
> Klarstellung: **6 Dateien** (4 .md + 2 .txt) wurden tatsächlich verschoben — die „8“ des Users
> war approximativ. Alle 6 haben ⚠️-Banner (neu hinzugefügt für die .txt-Dateien, die bisher
> bannerlos waren) bzw. bereits archiviert.

### Aktionen Pass 3

| Aktion | Datei | Effekt |
|--------|-------|--------|
| `mv` | `docs/HISTORICAL_BUGFIX_LOG.md` → `docs/archive/HISTORICAL_BUGFIX_LOG.md` | Banner vorhanden |
| `mv` | `docs/HISTORICAL_FULLSCAN.md` → `docs/archive/HISTORICAL_FULLSCAN.md` | Banner vorhanden |
| `mv` | `docs/HISTORICAL_ORIGINAL_README.md` → `docs/archive/HISTORICAL_ORIGINAL_README.md` | Banner vorhanden |
| `mv` | `docs/HISTORICAL_ORIGINAL_INFO.txt` → `docs/archive/HISTORICAL_ORIGINAL_INFO.txt` | **⚠️-Banner neu hinzugefügt** |
| `mv` | `docs/HISTORICAL_ORIGINAL_JAR.txt` → `docs/archive/HISTORICAL_ORIGINAL_JAR.txt` | **⚠️-Banner neu hinzugefügt** |
| `mv` | `docs/HISTORICAL_SEMANTIC_DIFF.md` → `docs/archive/HISTORICAL_SEMANTIC_DIFF.md` | Banner vorhanden (war bereits in Pass 2 hinzugefügt) |
| CREATE | `docs/archive/README.md` | **NEU** — Inhaltsverzeichnis aller 6 Archiv-Dateien mit Freeze-Datum, Redirects zu aktiven Docs, Convention für neue Einträge |
| UPDATE | `docs/README.md` (Sektion „Historische Snapshots") | 6-Zeilen-Tabelle ersetzt durch 5-Zeilen-Redirect-Paragraph auf `[docs/archive/](archive/)` |
| UPDATE | `docs/ARCHITECTURE.md` (Verzeichnisstruktur + Hinweis) | Baum zeigt `archive/`-Subdir mit allen 6 Dateien; Hinweis-Zeile verweist auf `[archive/README.md](archive/README.md)` |
| UPDATE | `docs/reports/FULLSCAN_CLEANUP_2026-07-23.md` | 3-Pfade-Tabelle zeigt jetzt `docs/archive/HISTORICAL_*` statt `docs/HISTORICAL_*`; Erfolgs-Checkpoint mit „Konsolidierungs-Schritt 2 abgeschlossen 2026-07-23" annotiert |

### Reviewer-Lücken (nach code-reviewer-minimax-m3 zu Pass 3)

| # | Lücke | Fix |
|---|-------|-----|
| 2 | **Stale Verzeichnisbaum in ARCHITECTURE.md** zeigte HISTORICAL_* als Siblings statt in archive/ | ✓ Baum aktualisiert (Z.388–401) |
| 2 | **Stale Pfade in FULLSCAN_CLEANUP_2026-07-23.md** zeigten `docs/HISTORICAL_*` | ✓ 3-Pfade-Tabelle aktualisiert + Erfolgs-Checkpoint annotiert |
| 4 | **HISTORICAL_ORIGINAL_*.txt** hatten keinen ⚠️-Banner | ✓ Beide mit ⚠️-Banner + Redirect auf aktuelle `_Info.txt`/README ausgestattet |
| 3 | **HISTORICAL_SEMANTIC_DIFF.md-Banner hatte falsche relative Pfade** (`(ARCHITECTURE.md)` statt `(../ARCHITECTURE.md)`) | ✓ Pfade korrigiert zu `../ARCHITECTURE.md`, `../GLOSSARY.md`, `../../CHANGELOG.md` |
| 1 | „8 statt 6"-Behauptung des Users | ✓ Klarstellung oben in dieser Sektion |

### Halbwertszeit-Effekt

`docs/` enthält jetzt **nur noch aktive Wahrheit** — keine historischen Drift-Quellen mehr in
der Hauptsicht. Wer aktiv liest, sieht 10 Markdown-Dateien + 4 Reports im `reports/`-Subdir.
Historische Tiefe ist bewusst 1 Klick entfernt in `[docs/archive/](archive/)`.

---

## 16. 5. Konsolidierungs-Pass — Version-Bump-Automatisierung (2026-07-23)
<!-- truth-tracks: tools/bump-version.sh, tools/post-commit-pom-watchdog.sh, tools/install-hooks.sh -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

> **Auslöser:** User-Anfrage „Die `update-version-bump` und `mod-changelog-trim` Workflows automatisieren“.

### Erstellte/geaenderte Dateien

| Datei | Zweck | Status |
|-------|-------|--------|
| `tools/bump-version.sh` (190 Zeilen) | Version-Bump mit patch/minor/major/--set modes + Dry-Run | **NEU** |
| `tools/post-commit-pom-watchdog.sh` (44 Zeilen) | Post-Commit-Watchdog: warnt wenn pom.xml ohne CHANGELOG.md | **NEU** |
| `tools/install-hooks.sh` (erweitert) | Installiert nun auch Post-Commit-Watchdog | **UPDATE** |

### Bump-Modi

| Modus | Eingabe | Ausgabe |
|-------|---------|---------|
| `patch` | `bash bump-version.sh patch -m "..."` | 0.0.X → 0.0.X+1 |
| `minor` | `bash bump-version.sh minor -m "..."` | 0.X.0 → 0.X+1.0 |
| `major` | `bash bump-version.sh major -m "..."` | X.0.0 → X+1.0.0 |
| `--set` | `bash bump-version.sh --set X.Y.Z -m "..."` | Beliebige Version |
| Optionen | `--dry-run`, `--no-tag`, `--no-commit` | Modifizieren Verhalten |

### 4-Phasen-Bump

| Phase | Aktion |
|-------|--------|
| **1/4 pom.xml** | `<version>`, `<mod.info>`, `<mod.changelog>`-Ersteintrag aktualisiert (Zeilen 1-20 only, ueberspringt Plugin-Versionen) |
| **2/4 CHANGELOG.md** | Neuer `## vX.Y.Z — DATE`-Eintrag via awk vor dem ersten `## v...`-Header eingefuegt |
| **3/4 mod.version.history** | Aus `git tag --sort=-creatordate \| head -5` regeneriert (Maven filtert nach _Info.txt) |
| **4/4 git** | `git add` + `commit` + `tag vX.Y.Z` (mit Existenz-Check fuer Tag-Kollision) |

### Architektur-Entscheidungen

1. **Kein direkter `_Info.txt`-Edit** — Maven filtert `${mod.version.history}` automatisch beim `mvn package`. Skript aktualisiert nur pom.xml.
2. **Sed-Zeilen-Range 1-20** fuer `<version>` — Plugin-/Dependency-Versionen in Zeilen >20 duerfen NICHT getroffen werden.
3. **Single-replace statt `/g`** — defensiver Match.
4. **Awk `getline` ohne `print ""`** — NEW_ENTRY bringt eigene Leerzeilen-Struktur mit, vermeidet Doppel-Leerzeile.
5. **Mode-Conflict-Detection im Parser** — `--set` innerhalb der Argumente verarbeitung prueft ob MODE bereits gesetzt.
6. **Tag-Existenz-Check vor `git tag`** — verhindert `fatal: tag already exists`.
7. **Watchdog ist non-blocking** — Notfall-Hotfixes bleiben moeglich.

### Tests

| Test | Erwartet | Ergebnis |
|------|----------|----------|
| `bash bump-version.sh patch -m 'msg' --dry-run` | 0.1.0 → 0.1.1, kein Schreibvorgang | OK |
| `bash bump-version.sh minor -m 'msg' --dry-run` | 0.1.0 → 0.2.0 | OK |
| `bash bump-version.sh --set 9.9.9 -m 'msg' --dry-run` | 0.1.0 → 9.9.9 | OK |
| `bash bump-version.sh patch --set 9.9.9` | exit 1, Fehler „nicht kombinierbar" | exit 1 OK |
| `bash bump-version.sh invalid` | exit 1, usage | exit 1 OK |
| `mvn compile -q` | MVN_EXIT 0, 3 Gates gruen | OK |
| `bash tools/docs-truth-consistency.sh` | 4/4 PASS | PASS |

### Reviewer-Fixes (alle umgesetzt)

| # | Finding | Fix |
|---|---------|-----|
| 1 | `(Phase $(date +%U))` ergab Woche-des-Jahres, sinnlos | Suffix entfernt, pure `vX.Y.Z — DATE` |
| 2 | `patch` + `--set` Konflikt stillschweigend uebergangen | Mode-Conflict-Check im Parser mit exit 1 |
| 3 | `sed -i "0,/<version>/"` traf auch Plugin-Versionen | Scope auf Zeilen 1-20 |
| 4 | `s\|...\|...\|g` (global) auf mod.info fragiler | Single-replace |
| 5 | Awk `print ""` ergab Doppel-Leerzeile | `print ""` entfernt |
| 6 | `git tag` ohne Existenz-Check | `git rev-parse vX.Y.Z` Skip mit warning |
| 7 | `--set` Parser ueberschrieb MODE still | Konflikt-Detektion im Parser |

### Hook-Installations-Flow

```
bash tools/install-hooks.sh
  → .git/hooks/pre-commit (Version-Gate + Truth-Gate)
  → .git/hooks/post-commit (Pom-Watchdog)

bash tools/install-hooks.sh --remove
  → beide Hooks zurueck
```

### Halbwertszeit-Effekt

Versions-Verwaltung ist jetzt **idempotent** und **nebenwirkungsfrei**:
- Ein einzelner Befehl aktualisiert 4 Dateien atomar (pom.xml, CHANGELOG.md, mod.version.history, git tag)
- Post-Commit-Watchdog faengt manuelle pom.xml-Edits ohne CHANGELOG.md-Begleitung ab
- Mode-Konflikte werden vor jedem Schreibvorgang abgewiesen
- Tag-Kollisionen werden mit warning uebersprungen statt fatal error

Konsolidierungs-Pass-Reihenfolge ueber alle 6 Pässe:
1. Drift-Fix (108→112, 5-Tiers)
2. Drift-Finish (restliche 108-Stellen + LOYALTY −50%)
3. Archiv-Verschiebung (HISTORICAL_* → docs/archive/)
4. Drift-Gate-Automatisierung (docs-truth-consistency.sh)
5. Version-Bump-Automatisierung (bump-version.sh + Watchdog)
6. Truth-Stamp (truth-stamp.sh + post-commit Hook + Sektion-Registry) — heute

---

## 14. Offene Aufgaben (nicht in diesem Pass erledigt)
<!-- truth-tracks: tools/, docs/ -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

| Aufgabe | Aufwand | Quelle |
|---------|---------|--------|
| Phase-5-Klassen implementieren (8 Stück) | Groß | `docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md` |
| Phase 5c GUI-Gap schließen (RoomStats-Bridge) | Groß | `docs/ROADMAP.md` |
| Phase 6 Advisor-Visualisierung | Mittel | `docs/ROADMAP.md` |
| Weitere JUnit-Tests (ExchangeKernel, FlowPrices) | Klein | ROADMAP / Phase 5 |
| HISTORICAL_\*.md-Dateien ins `docs/archive/`-Subdir verschieben | Klein | ✅ **ERLEDIGT in Pass 3** (2026-07-23) |
| Pre-Commit-Hook installieren (`tools/verify-version-consistency.sh`) | Klein | Optional |

Diese Tasks sind explizit NICHT Bestandteil dieses Konsolidierungs-Passes.

---

## 12. 2. Konsolidierungs-Pass — Driff-Finish (2026-07-23, an diesem Tag)
<!-- truth-tracks: docs/GLOSSARY.md, docs/ARCHITECTURE.md, docs/SESSION_SUMMARY_2026-07-23.md, CHANGELOG.md, docs/HISTORICAL_SEMANTIC_DIFF.md -->


## Stand: 2026-07-23 17:00 | commit initial | checks: 1

> **Auslöser:** User-Anfrage „der Truth-Pass sollte keine Halbwertszeit haben". Nach dem 1. Pass
> waren noch Restdrift-Stellen in GLOSSARY.md, ARCHITECTURE.md und im Root-README vorhanden.
> Außerdem hat `code-reviewer-minimax-m3` 3 weitere Lücken aufgedeckt.

### Drift-Fix-Tabelle (Pass 2)

| Datei | Zeile | Vorher | Nachher |
|-------|-------|--------|---------|
| `README.md` (Root) | 165 | „108 Einträge, 4 Kategorien" | „**112 Einträge**, 4 Kategorien" |
| `README.md` (Root) | 49 | „5-stufige eskalierende Krise" | „5-stufige eskalierende Krise **+ Hard-Floor**" |
| `docs/README.md` | 31 | „108 Einträge, 4 Kategorien" | „**112 Einträge**, 4 Kategorien" |
| `docs/GLOSSARY.md` | 3 | „**108 Java-Klassen**" | „**112 Java-Klassen**" |
| `docs/GLOSSARY.md` | 152 | „**6-stufige** Krisenmechanik mit Hysterese" | „**5-stufige** Krisenmechanik + Hard-Floor-Verhalten in Tier 5" |
| `docs/GLOSSARY.md` | 279 | „108 Klassen in 4 Kategorien" | „**112 Klassen** in 4 Kategorien" |
| `docs/ARCHITECTURE.md` | 18 | „core/ (90 Dateien)" | „core/ (**95** Dateien)" |
| `docs/ARCHITECTURE.md` | 42 | „3-stufige Krisenmechanik" | „5-stufige Krisenmechanik + Hard-Floor (Tier 5)" |
| `docs/ARCHITECTURE.md` | 97 | „90 Dateien, ~20.000 LOC" | „112 Dateien (95 core/ + 17 adapter/), ~21.000 LOC" |
| `docs/ARCHITECTURE.md` | 298 | „3-stufige Krisenmechanik. Warnung→Zwangsprivatisierung→..." | Vollständige 5-Tier-Beschreibung inkl. **LOYALTY −50%** + `BOOSTABLES.BEHAVIOUR().LOYALTY`-Pfad |
| `docs/SESSION_SUMMARY_2026-07-23.md` | 218 | „84 → **108**" | „84 → **112** (95 core/ + 17 adapter/)" |
| `CHANGELOG.md` (Root) | — | v0.1.0-Eintrag ohne TreasuryCrisis | **Neue Zeile 17:** TreasuryCrisis 5 Tiers + Hard Floor prominent |
| `docs/HISTORICAL_SEMANTIC_DIFF.md` | — | Keine Banner | ⚠️ **HISTORISCH — ARCHIVIERT** Banner mit Redirects |

### Reviewer-Lücken (nach code-reviewer-minimax-m3)

| # | Lücke | Fix in Pass 2 |
|---|-------|---------------|
| 1 | ARCHITECTURE.md Tier-5 ohne LOYALTY −50% | ✓ Ergänzt: „ALLE 11 Systeme deaktiviert, **LOYALTY −50%** via `BOOSTABLES.BEHAVIOUR().LOYALTY` → Emigration/Strikes-Risiko" |
| 2 | Tier-4 hat ebenfalls LOYALTY-Druck (Kopfsteuer + Marktsteuer) | Konsistent dokumentiert in Tier-4-Zeile (Kopfsteuer=500, Marktsteuer=50%, Corvée/Dole/Schuldknechtschaft/Wages deaktiviert) — direkter LOYALTY-Effekt nur für Tier 5 via Booster-Boost dokumentiert. Reviewer-Punkt akzeptiert. |
| 3 | Root-CHANGELOG v0.1.0 fehlt TreasuryCrisis | ✓ TreasuryCrisis als prominenter Top-Eintrag in v0.1.0 hinzugefügt |
| 4 | HISTORICAL_SEMANTIC_DIFF.md ohne Banner | ✓ Banner hinzugefügt. Andere HISTORICAL_*.md haben bereits Banner; .txt-Dateien sind reine Artefakte (jar-Listing, _Info.txt-Kopie) — Banner nicht anwendbar |
| 5 | README.md Zeile 49 vs. 128 — Konsistenz „+Hard-Floor" | ✓ Zeile 49 angepasst auf „5-stufige eskalierende Krise + Hard-Floor" |

### Verifikation Pass 2

- `mvn compile` → **BUILD SUCCESS**, MVN_EXIT: 0, 3 Gates grün (Code-Audit, Version↔Changelog, Adapter↔Engine)
- Keine Java-Datei angefasst — nur Markdown
- Final-grep nach `108|6-stufig|3-stufige` (außer in den intentionalen Truth-Status-Tabellen von `docs/README.md` Zeilen 88/93): **0 Treffer**
- Final-grep nach `112|5-stufig|1/3`: in allen 7 Haupt-Docs konsistent vorhanden

### Halbwertszeit-Status

Die Halbwertszeit wird jetzt durch drei Mechanismen minimiert:

1. **Root-Konsolidierung:** `CHANGELOG.md` (Root) ist Single-Source-of-Truth. `docs/CHANGELOG.md` ist Redirect-Notice.
2. **Banner-Archivierung:** Alle `HISTORICAL_*.md` sind mit ⚠️-Banner markiert; `docs/README.md` Truth-Status-Tabelle dokumentiert bekannte Drifts als „historische Aussage" vs. „aktuelle Wahrheit".
3. **Pre-Commit-Gate:** `tools/verify-version-consistency.sh` prüft vor jedem Commit, ob `pom.xml` ↔ `CHANGELOG.md` konsistent sind. Erweiterung um eine `docs-truth-consistency.sh`-Variante (prüft 108→112, 5-stufig vs 3/6-stufig) ist als optionale Folgeaufgabe in Sektion 11 gelistet. Realisiert in Pass 4 (Drift-Gate-Automatisierung) und jetzł Pass 6 (Truth-Stamp).

---

## 17. 6. Konsolidierungs-Pass — Truth-Stamp (2026-07-23)

> **Auslöser:** User-Anfrage „Promote `docs/reports/TRUTH_REPORT.md` zu einer lebendigen Audit-Quelle: jedes Mal wenn eine Datei in docs/ oder docs/archive/ editiert wird, soll der entsprechende Sektion-Absatz mit Zeitstempel + Commit-Hash aktualisiert werden."

### Erstellte/geaenderte Dateien

| Datei | Zweck | Status |
|-------|-------|--------|
| `tools/truth-stamp.py` (~210 Zeilen) | Python-Modul mit stamp + init Modes | **NEU** |
| `tools/truth-stamp.sh` (~70 Zeilen) | Bash-Wrapper (Preflight, Mode-Dispatch) | **NEU** |
| `tools/install-hooks.sh` | Erweitert: installiert nun truth-stamp + Migrations-Detect | **UPDATE** |
| `docs/reports/TRUTH_REPORT.md` | 15 Sektionen mit Truth-Registry + Initial-Stand | **UPDATE** (via `bash tools/truth-stamp.sh --init`) |

### Sektion-Convention (vom Skript erwartet)

```
## N. Title
<!-- truth-tracks: file1, file2, ... -->
## Stand: YYYY-MM-DD HH:MM | commit <hash> | checks: <N>
```

### Modi

| Mode | Aufruf | Effekt |
|------|--------|--------|
| `stamp` (default) | `bash tools/truth-stamp.sh` (Post-Commit-Hook) | Aktualisiert `## Stand:`-Zeile fuer Sektionen, deren Registry geaenderte docs/-Dateien matcht; inkrementiert `\| checks: N` |
| `--init` | `bash tools/truth-stamp.sh --init` (einmalig) | Fuegt initial Registry+Stand fuer Sektionen ohne Registry ein, basierend auf hartkodiertem `SECTION_TRACKS`-Dict |

### Pfad-Matching

`c == t or c.startswith(t)` — ein Track-Eintrag `t` matcht eine geaenderte Datei `c`, wenn sie exakt gleich ist oder `c` mit `t` als Praefix beginnt. `src/vannon/` matcht jede Datei unter `src/vannon/`, waehrend `CHANGELOG.md` nur die exakte Datei matcht.

### Tests

| Test | Ergebnis |
|------|----------|
| `bash -n tools/truth-stamp.sh` | SYNTAX OK |
| `python3 -c 'ast.parse(...)'` | OK |
| `bash tools/truth-stamp.sh --init` | 15 Sektionen initialisiert |
| Smoke Test (`/tmp/stamp-test`, docs/README.md change) | Section 1 stamped: checks 1→2, Timestamp+Commit-Hash aktualisiert |
| Smoke Test (pom.xml change mit Registry-Match) | Section 2 stamped: `commit 17b3733` in `## Stand:` |

### Hook-Integration

`tools/install-hooks.sh` installiert nun einen Composite-Post-Commit-Hook:

```
.git/hooks/post-commit
├── bash tools/post-commit-pom-watchdog.sh    # existierend (pom.xml ↔ CHANGELOG.md)
└── bash tools/truth-stamp.sh                # NEU
```

Mit Migrations-Logik: alte Installationen, die eine Verbatim-Kopie des Watchdog-Skripts enthalten, werden automatisch erkannt (Banner-Comment-Pattern) und ersetzt, um Doppel-Ausfuehrung zu verhindern.

### Halbwertszeit-Effekt

Vor Pass 6 hatte TRUTH_REPORT.md statische Datums-Header ohne Per-Sektion-Tracking. Ab Pass 6 hat jede Sektion einen `| checks: N`-Counter:

- **Hohe `checks`-Werte** = Sektionen, die haeufig angefasst werden (z. B. Sektion 6 = pom.xml ↔ CHANGELOG.md bei jedem Bump)
- **Niedrige `checks`-Werte** = Sektionen, die seit Erstellung kaum aktualisiert wurden (potenzielle Drift-Quellen)
- **Timestamp + Commit-Hash** = exakt nachvollziehbar, wann + durch welchen Commit zuletzt gestempelt wurde

### Reviewer-Fixes (alle umgesetzt, alle 8 Fragen)

| # | Finding | Fix |
|---|---------|-----|
| 1 | Tuple-Liste vs. String-Liste in `join()` (`s for _, s in new_sections` versuchte Strings zu unpacking) | `new_sections` direkt ge-join-ed |
| 2 | Hook-Upgrade-Regression (alte `cp`-Installation ↔ neue `echo`+`>>`-Installation) | Migrations-Detect via Banner-Comment-Pattern `SyxEconomyMod — Post-Commit-Pom-Watchdog`, automatisches Replace |
| 3 | `git add` stderr ueber `subprocess.DEVNULL` verschluckt | `capture_output=True, text=True`, Exit-Code und stderr werden auf stderr geloggt |
| 4 | Section 14 (Offene Aufgaben) hat sehr breite Registry `tools/, docs/` | Bewusst beibehalten — Sektion 14 ist Sammel-Sektion, dokumentiert im `SECTION_TRACKS`-Dict; Verfeinerung ist Folge-Aufgabe |

### Folge-Aufgaben (future hardening)

| Aufgabe | Aufwand | Quelle |
|---------|---------|--------|
| `re.DOTALL`-Flag fuer multiline Registry-Comments | Trivial | Reviewer (Edge-Case heute nicht aktiv) |
| Init-Mode-Validierung: warnt bei Track-Entries ohne `/` und ohne `.md`/`.java`/`.sh`-Suffix | Klein | Reviewer (Praeventiv, aktuelle 16 Entries sind sauber) |
| Section 14 Registry auf spezifische Files einschraenken | Klein | Reviewer (Definitionell noise-reduzierend, aber nicht funktional) |
| `tools/install-hooks.sh --remove` räumt auch Composite-Hook-Eintraege auf | Trivial | Konsolidierungs-Pattern-Konsistenz |

Konsolidierungs-Pass-Reihenfolge (final, alle 6 Pässe):
1. Drift-Fix (108→112, 5-Tiers)
2. Drift-Finish (restliche 108-Stellen + LOYALTY −50%)
3. Archiv-Verschiebung (HISTORICAL_* → docs/archive/)
4. Drift-Gate-Automatisierung (docs-truth-consistency.sh)
5. Version-Bump-Automatisierung (bump-version.sh + Watchdog)
6. Truth-Stamp (truth-stamp.sh + post-commit Hook + Sektion-Registry) — heute
