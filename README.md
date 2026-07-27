# SyxEconomyMod

> **Version:** v0.13.63 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` validiert dies vor jedem `mvn compile`.

> Wirtschaftssimulation für Songs of Syx V71.
> Bürger verdienen Löhne, zahlen Steuern, kaufen Nahrung, mieten Wohnungen.
> Firmen maximieren Profit, stellen Arbeiter ein. Der Staat führt die Kasse — mit Krisenmechanik.

---

## Quickstart

```bash
mvn validate                 # Preflight: 9 Gates (Sync, Audit, Version, Adapter, Bytecode, Sim, Schema, Balance, God-Class) (Sync, Audit, Version, Adapter)
mvn clean install -DskipTests  # Baut + installiert das Mod + bumpt version 0.0.1+
# → target/out/SyxEconomyMod/ ins Mod-Verzeichnis kopieren
```

Mod-Pfad: `${user.home}/.local/share/songsofsyx/mods/SyxEconomyMod/V71/`

**Spiel-Version required:** Songs of Syx V71.44 oder kompatibel.

---

## Was der Mod macht

| System | Kern |
|---|---|
| **Arbeitsmarkt** | Firmen bieten Löhne, Arbeiter priorisieren nach Profitabilität, marginaler Gewinn gecapped bei `wageMax=1000` |
| **Geldumlauf** | Jeder Bürger hat ein Wallet (`v0.0.6` stage-gated: 200/500/2000/5000 D Seed). Verdient, kauft, spart, beerbt |
| **Marktpreise** | Supply/Demand → Scarcity-Signal 0..1 → FlowPrices (`anchor × (1 + max(0, 1−coverage)²)`) |
| **Steuern & Staat** | Kopfsteuer, Marktsteuer, Religion, Liturgie — mit `perHeadTaxExemptionThreshold=500` D Armutsfreigrenze |
| **Staatskasse** | 5-stufige TreasuryCrisis (−5K/−50K/−250K/−1M/−5M) + Hard-Floor in Tier 5 (alle 11 Systeme aus, Loyalty −50%) |
| **Vermögen** | Gini-Koeffizient → Loyalty-Booster (`loyaltyAtMaxGini=0.85`). Property-Markt, Miete, Dividenden |
| **UI** | 5 Fenster (Übersicht, Wirtschaft, Staat, Quickview, +EconHud), 16 interaktive Tabs, 6 Hotkeys |

---

## Struktur

```
src/vannon/syx/economy/        ← Mod-Eigentum (128 .java-Dateien, ~23k LOC)
├── core/        100 Dateien,  19.247 LOC   (Economy-Sim, Wallets, Firmen, Logs)
├── adapter/      17 Dateien,  1.162 LOC   (Engine-API-Wrapper, Reflection-Kapselung)
├── ui/           5 Dateien,   2.345 LOC   (4 Fenstrer + Base, 16 inline Tabs)
└── benchmark/    1 Datei,     ~200 LOC    (Reflection-vs-MethodHandle-Messung)

src/settlement/room/..         ← 4 Package-Private Brücken (compile-time-safe Zugriff)
                                  (LaborMarketAccess, EconomyTavern/Eatery/CanteenAccess)
```

| Schicht | Aufgabe | Wahrer-API-Zugriff |
|---|---|---|
| Vanilla Engine (Songs of Syx) | Unverändert | — |
| Adapter-Layer (`adapter/`) | Engine-Wrapper | Reflection (nur im Adapter-Konstruktor) |
| Wirtschafts-Logik (`core/`) | Alles andere | nur über Adapter |
| UI (`ui/`) | 4 Fenster + 16 Tabs | `EconWindowBase.lastSet()` für Top-Rendering, Vanilla `SPanel`/`GCOLOR`-Theming |

Vollständiger Schicht-Plan: siehe [`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## Fenster & Hotkeys (16:9-Übersicht)

| Fenster | Tabs | Hotkey |
|---|---|---|
| **WindowOverview** | Dashboard, Demografie, Berater, Immobilien | Numpad + |
| **WindowEconomy** | Märkte, Preise, Betriebe, Löhne, Subventionen, Bücher | Numpad − |
| **WindowState** | Lager, Finanzen, Werke, Soziales, Glaube, (Debug versteckt) | Numpad ∗ |
| **WindowQuickview** | kompakte Anzeige (Gini+Treasury+Stage+Food-Days) | Numpad 0 |
| DebugTracer dump | — | Numpad / |
| Alle Fenster schließen | — | ESC |

Hotkeys via `pollHotkeys()` in `InstanceScript` mit Edge-Detection (`Hk.java`-Pattern). Clean-Switching: das Ziel-Fenster toggelt, alle anderen schließen falls offen.

---

## Stam-Dokumente (Single Source of Truth)

| Datei | Inhalt |
|---|---|
| [`README.md`](README.md) | Diese Datei (Index, Quickstart, Fenster/Hotkeys) |
| [`CHANGELOG.md`](CHANGELOG.md) | Vollständige Release-Historie v0.0.1 → v0.13.2 |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Schichtenmodell, Modul-Inventar, Save/Load-System |
| [`ROADMAP.md`](ROADMAP.md) | Offene Arbeit aus BACKLOG + aktive Pläne in `docs/superpowers/plans/` |
| [`GLOSSARY.md`](GLOSSARY.md) | Klassen-Glossar (4 Kategorien) |
| [`pom.xml`](pom.xml) | Build-Metadaten + Version of Record |
| [`_Info.txt`](_Info.txt) | Steam-Workshop-Manifest (Maven-Filter-Template) |

**Sync-Disziplin:** Die Versions-Stempel oben in jeder Doku-Datei MÜSSEN identisch sein mit `pom.xml` `<version>`. Der Preflight-Gate `tools/verify-doc-sync.sh` (eingebunden in `mvn validate`) blockt den Build bei Drift.

---

## Tools

```bash
tools/
├── verify-doc-sync.sh             # Preflight-Gate (NEU): 7 Stam-Docs ↔ pom.xml
├── build-gate.sh                  # Master-Orchestrator (Audit, Version, Adapter, Sync)
├── verify-version-consistency.sh  # pom.xml ↔ CHANGELOG + _Info.txt
├── docs-truth-consistency.sh      # Drift-Heuristik (Treffer auf deprecated Behauptungen)
├── code-audit.sh                  # catch(Throwable), printStackTrace, IdentityHashMap
├── bump-version.sh                # patch|minor|major|set [--no-commit] [--no-tag] [--bump-only]
├── phase47-shield.sh              # CI-Gate für P0/P1-Blocker
├── truth-stamp.py                 # Commit-Hook: Docs auf Aktualität
└── install-hooks.sh               # Git-Hooks installieren
```

### Manuell ausführen

```bash
bash tools/verify-doc-sync.sh      # einzelne Stam-Doc-Sync Prüfung
bash tools/build-gate.sh --strict  # alle 4 Gates im Strict-Mode
bash tools/bump-version.sh patch --dry-run  # Patch-Bump simulieren (ohne Schreib-Effekt)
```

### Auto-Patch-Bump

Jeder `mvn clean install` ruft den Antrun-Hook der install-Phase auf, der `tools/bump-version.sh patch --bump-only` ausführt. Skip mit `-Dskip.bump=true`.

### Diagnostic Tools (Python)

Nicht-Gate-Tools für tiefergehende Wirtschafts-Analyse. Reine Berechnung, kein Java-Build nötig.

| Tool | Zweck | Aufruf |
|---|---|---|
| `audit-sim-logic.sh` — `tools/audit-sim-logic.sh` | Audit-Skript für die 4 Scarcity-Kaskaden-Formeln gegen die echten `FlowPrices`/`LocalPrices`/`EconConfig`-Klassen. Druckt PASS/FAIL pro Cascade. Ersetzt `tools/scarcity_sim.py` (gelöscht in Sprint 8, Commit `2ac5191`). | `bash tools/audit-sim-logic.sh` |
| `rebalance_plots.py` / `rebalance_dashboard.ipynb` | Pandas/Notebook-Auswertung der `DiagnosticExporter`-CSV-Snapshots. | `jupyter notebook tools/rebalance_dashboard.ipynb` |

#### Scarcity-Simulator (entfernt in Sprint 8)

`tools/scarcity_sim.py` wurde in Sprint 8 (Commit `2ac5191`, Begründung *"0 cross-references, DEAD"*) gelöscht — die Dead-Code-Heuristik hatte nur Code-Dateien gescannt, nicht Markdown. Die vier Szenario-Beschreibungen oben sind als historische Spec weiterhin unter `CHANGELOG.md` v0.13.10 archiviert; die aktive Validation passiert über `tools/audit-sim-logic.sh` (siehe Diagnostic-Tools-Tabelle).

Sprint 9 plant unter `T-9.4` / `7-1a`–`b` die spec-getriebene Re-Etablierung als `tools/balance-smoke.sh` mit Golden-Snapshot-Vergleich (keine Code-Wiederherstellung der gelöschten Datei). Der Sprint-9-Workflow folgt damit Anti-Bias-Wording gemäß `WORKFLOW.md` Regel 1.6 (0/N als valider Ausgang).

#### Exit-Codes

Das Skript hat aktuell keinen Exit-Code-Bound (kein CI-Gate-Verhalten). Sprint 9 plant unter `T-9.4` / `7-1a`–`b` die spec-getriebene Re-Etablierung als `tools/balance-smoke.sh` (Golden-Snapshot-Vergleich statt Code-Wiederherstellung der gelöschten Datei).

---

## Build-Gates (Reihenfolge = Abhängigkeiten)

| # | Gate | Skript | Phase | Hart-Block? |
|---|---|---|---|---|
| 1 | Stam-Doku-Sync | `verify-doc-sync.sh` | `validate` | ✅ |
| 2 | Code-Audit | `code-audit.sh` | `validate` | ✅ (bei `printStackTrace`/leeren-catches) |
| 3 | Version ↔ Changelog | `verify-version-consistency.sh` | `validate` | ✅ |
| 4 | Adapter ↔ Engine-Signaturen | inline in `build-gate.sh` | `validate` | ✅ |
| 5 | Bytecode-Injection Audit | `audit-bytecode.sh` | `validate` | ✅ |
| 6 | Sim-Logic Audit | `audit-sim-logic.sh` | `validate` | ✅ |
| 7 | Schema-Validierung | inline (YAML ↔ Adapter) | `validate` | ✅ |
| 8 | Balance-Regression | `balance-regression-check.sh` | `validate` | ✅ |
| **9** | **God-Class-Guard** | **`god-class-guard.sh`** | **`validate`** | **✅** |

Plus dokumentarisch: `docs-truth-consistency.sh` (Pre-Commit-Hook via `install-hooks.sh`).

---

## Test

```bash
mvn test                  # 138+ JUnit-Tests in test/java/
mvn jacoco:report         # Coverage-Report für die 7 Kernel-Klassen
```

---

## Credits

- **Original:** TiredGirl4's Economy Mod
- **Entwicklung:** vannon091118
- **Engineering:** Freebuff-assisted
## Datenmatrix (BINDUNGSMATRIX.csv)

`BINDUNGSMATRIX.csv` ist die kanonische Reference-Data fuer Hebel-Verifikation
zwischen SyxEconomyMod ↔ Songs-of-Syx V71.44. Header:
`ID;Datenpunkt;Wert-Typ;Quelle-Klasse;Zugriffspfad;Zugriffsart;Mod nutzt;UI-Kandidat;Status;Lücke;ModVerifiziert`

**Marker-Spec (Spalte 11 — ModVerifiziert):**
- `++`  HEBEL-Claim UND Mod-Code bestaetigen sich gegenseitig
- `??`  HEBEL-Claim korrekt, aber Mod-Code hat keinen Referenten (Orphan)
- `?`   Vage/unclear, nicht greppbar
- `/`   REBUTTAL — HEBEL sagt X, Mod tut Y
- (leer) Unentschieden

**Bauen:** `python3 tools/build_bindungsmatrix.py` (kanonisch)

**Statistik:** aktuell 332 Zeilen, 252 HEBEL-Sub-Datenpunkte (A1=2 ... B7=11 ... N95=9) + 51 Mod-API (`X.*`) + 25 Engine-API (`Y.*`) + 3 MZ-Summary.

