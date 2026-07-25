# Backlog

> **Auto-managed.** Entries flow in from:
> - `tools/consolidate-live-notes.sh` — `gap:net-new` and `ux:papercut` tags
> - Manual backlog triage during plan reviews
>
> Format: `### [ID] Title` with Source, Severity, Status.
> Severity: 🔴 P0 (crash/data-loss) · 🟠 P1 (broken feature) · 🟡 P2 (papercut) · 🟢 P3 (nice-to-have)

---

## Sprint-Snapshot (aktuell)

Letzter abgeschlossener Sprint: **Mod-Economy T5–T13** (11 Tasks: T5 B-001, T6 B-009, T7 B-004, T8 H8, T9 revertFireSale, T10 diagnosticsExport, T11 HEBELKARTE-Superseded, T12 AccessAutomation-Reset, T13 Static-Audit 5-Klassen-Reset + BINDUNGSMATRIX-Canonical).

BINDUNGSMATRIX.csv (332 Zeilen, 11 Spalten) ist seit Sprint T5–T13 die kanonische Reference-Data fuer Hebel-Verifikation. HEBELKARTE.md wurde in diesem Sprint geloescht.
   Marker-Spec uebernommen in BINDUNGSMATRIX.csv Spalte 11 (ModVerifiziert):  ++ verified · ?? orphan · ? unclear · / rebuttal.


## 2026-07-24 Session — Live-Test Findings

### B-001: FlowMeter-Coverage-Gap — Farms/Pastures nicht gesampelt
**Source:** `gap:net-new` · **Severity:** 🟠 P1 · **Status:** Open

`FlowMeter.sample()` iteriert nur `SETT.ROOMS().industries.all`. FARM_GRAIN, FARM_FRUIT, FARM_COTTON, WORKSHOP_POTTERY sind keine Vanilla-Industries → nie gesampelt → `profit_per_day=0.00` in CSV. ~20 LoC Fix: zusätzlich `SETT.ROOMS().ins()` iterieren für Räume die `ROOM_PRODUCER_INSTANCE` implementieren.

### B-002: AccessAutomation-Spam — 14 Statusmeldungen/Tick
**Source:** `ux:papercut` · **Severity:** 🟡 P2 · **Status:** Open

"AccessAutomation room scan disabled after earlier failure" spammt 14×/Tick ins Spieler-Chronik-Fenster. Der Rate-Limiter aus v0.1.2 greift nur für NPEs, nicht für Statusmeldungen. Fix: Rate-Limit auch auf die Statusmeldung anwenden.

### B-003: Advisor-Widerspruch — "Defizit" + "Staatskasse stabil"
**Source:** `ux:papercut` · **Severity:** 🟡 P2 · **Status:** Fixed in UI-Refactor

Finanzen-Tab zeigte gleichzeitig "Defizit" (rot) und "Staatskasse stabil" (grün). Zwei Subsysteme, keine Absprache. Fix: `OverviewTabs.AdvisorTab` aggregiert Einnahmen/Ausgaben zentral und zeigt einen einzigen konsistenten Status (Defizit / Knapp / Stabil). — siehe Plan 2026-07-24-3-window-ux-refactor.md (Task 6).

### B-004: Vermögensklassen-Drift — 24 "Elite" verschwinden
**Source:** `bug:silent` · **Severity:** 🟠 P1 · **Status:** Open

"Vermögens-Verteilung" zeigt 30 Mittelstand + 24 Elite = 54. "Bürger-Klassen" zeigt 28 Arm + 23 Mittelstand = 51. 3 Bürger fehlen, 24 Elite tauchen in Kategorie-Ansicht nicht auf. Zwei Klassifikationssysteme, keine Synchronisation.

### B-005: Oddjob-Clamp — Placebo mit Warnetikett
**Source:** `bug:silent` · **Severity:** 🟠 P1 · **Status:** Open

`setPay()` loggt nur Warnung via `System.err.println`, erzwingt aber keinen Cap. `EconomyWindow`-Slider + Save/Load schreiben direkt ungeclampt. Fix: Harte Grenze im Setter.

### B-006: IdentityHashMap — 3 Dateien verbleibend, Long-Key-Migration offen
**Source:** `gap:net-new` · **Severity:** 🟠 P1 · **Status:** Open

`IdentityMapRegistry` cleart Maps beim Load (lauter Datenverlust statt stiller Korruption), aber echte Long-ID-Migration steht noch aus. Aktuell 3 Dateien in `core/` nutzen `IdentityHashMap` (außerhalb von `IdentityMapRegistry.java`). Phase-4.7-Blocker #8.

### B-007: catch(Throwable) — 0 Sites in core/
**Source:** `gap:net-new` · **Severity:** 🟡 P2 · **Status:** Done

Von ehemals 27 Sites auf 0 reduziert. `phase47-shield.sh` blockt Regressionen.

### B-008: EngineSeams-Direkt-Calls — 31 in core/ (Ziel: 0)
**Source:** `gap:net-new` · **Severity:** 🟡 P2 · **Status:** Open

Phase-5-Blocker #2: 31 direkte EngineSeams-Calls im Core. Ziel: 0.

### B-009: Hungersignal ohne Bevölkerungskonsequenz
**Source:** `balance:drift` · **Severity:** 🟠 P1 · **Status:** Open

Save `48024362381900`: 20 Tage `starving_signal=1`, `food_days=0`, Bevölkerung wächst trotzdem. Entweder zu späte Kopplung oder keine Kopplung an Immigration/Reproduktion.

### B-010: carpenter/targetWage=0 in FlowPrices
**Source:** `bug:silent` · **Severity:** 🟡 P2 · **Status:** Open

In der FlowMeter-Coverage-Analyse entdeckt: `carpenter.targetWage=0` in FlowPrices (sollte ≈ 50 sein). Wage-Signal für Carpenter-Beruf existiert nicht.

---

## Frühere Backlog-Einträge (vor 2026-07-24)

### B-011: CI-Gate-Integration für `tools/scarcity_sim.py`
**Source:** `gap:tools` · **Severity:** 🟢 P3 · **Status:** Open

Sim (`python3 tools/scarcity_sim.py`) derzeit ohne Exit-Code-Bound. Geplant: Golden-Snapshot-Vergleich in `tools/build-gate.sh --strict` mit 5%-Toleranz gegen Excel/pandas-Referenz. Aktuell nicht zugesagt — kann live in `tools/scarcity_sim.py` manuell ausgeführt werden (siehe README §Diagnostic Tools). Owner: TBD.

---

_Keine — der Backlog wurde mit dieser Session initialisiert. Frühere Plan-Fragmente sind in ROADMAP.md absorbiert._
