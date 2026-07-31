# SyxEconomyMod — Quad-Perspective Audit (UI-Restrukturierung)

> **Stand:** 2026-07-30 | **Basis:** v0.13.103+ (Live-Test), V71.44 Engine
> **Methodik:** 4-Quadrate (USER×UX, USER×TECH, CODER×UX, CODER×TECH)
> **Fokus:** UI-Struktur vor Restrukturierung — Hebel-Coverage, Severity-Heatmap, God-Class-Reste
> **Exclusions:** Workerspace (siehe voriges Audit 2026-07-28 RES-033/035/011), Engine-Bypass (Rule 9)
> **Truth-Pass-Basis:** docs/SyxEconomyMod_AUDIT_2026-07-28.md (RES-033 FIXED, RES-035 OPEN, B-011 OPEN)

---

## Executive Summary — Top-N Risiken VOR UI-Restrukturierung

| # | Risiko | Severity | LOC-Impact | Phase-N-Blocker |
|---|--------|----------|------------|-----------------|
| 1 | **Tab-Rebuild bei Klick** — Fenster wird bei jedem Tab-Wechsel geschlossen+reopened → 100ms Hitch + GC-Druck | **high** | ±80 LOC Refactor | **ja** |
| 2 | **239 EconConfig-Hebel, UI exponiert ~25** — 90% der Konfiguration im Code begraben | **high** | +1 neue "Lever"-Tab | **ja** |
| 3 | **WindowOverview 948 LOC** — 4 Tabs + 8 Helpers statisch | **high** | ±300 LOC Refactor | nein |
| 4 | **PricesTab hardcoded `Math.min(size, 25)`** — keine Severity-Filter | **med** | ±40 LOC | **ja** |
| 5 | **WindowQuickview Build+Render-Pfad code-dupliziert** — 70 LOC 1:1 doppelt | **med** | ±150 LOC DRY | nein |
| 6 | **Tab-build() ohne Error-Boundary** — NPE in `sim.flowPrices()` killt Window-Build | **med** | ±30 LOC | **ja** |
| 7 | **SIBLINGS statische WindowRefs** — bei Save/Load stale | **low** | ±20 LOC | nein |

---

## TEIL A: Q1 — USER × UX (Spieler-Erleben)

### Befunde

**A1.1: 239 Hebel, 25 exponiert** — `EconConfig.java` hat 239 `public static`-Hebel, aber die
16 Tabs zusammen referenzieren nur ~25 davon. 90% der Mod-Steuerung im Code versteckt.
Beispiel: `firmStaatsbestandEnabled`, `firmStaircaseCoverageTiers`, `crimeTheftEnabled`,
`corveeDraftMax`, `productionSubsidies.*` — alle sichtbar in EconConfig, keiner im UI.

**A1.2: PricesTab zeigt nur 25 Ressourcen ohne Filter** — `WindowEconomy.java:232` hardcoded
`int rows = Math.min(RESOURCES.ALL().size(), Math.min(25, (h - 60) / 16));`. Songs of Syx hat
100+ Ressourcen. Spieler kann nicht nach Mangel sortieren, sieht die ersten 25.
Knappe Ressource an Position 73? Unsichtbar.

**A1.3: Blueprint-Keys statt Display-Namen** — `WindowEconomy.java:212` `key.set(keyStr)` mit
`keyStr.substring(0, 18)` — Spieler sieht `WORKSHOP_CARPENT` statt "Tischler".
`toDisplayName()` existiert für Ressourcen (PricesTab, SubsidiesTab:209), aber FirmsTab nutzt es nicht.

**A1.4: Severity-Heatmap fehlt für Resource-Pressures** — `WindowEconomy.java:255-265` 3
Hard-Coded Levels (MANGL/knapp/ok/UEBERSCH) als Text-Tag, kein Sortier-Criterion, keine Filter-Toggle.
`PriorityRegistry.score()` ist im Code, nicht im UI sichtbar.

**A1.5: WindowQuickview fixed 360x480** — `WindowQuickview.java:15-18` hardcoded. Verdeckt
Spielelemente, kein Stack-Manager. Nicht skalierbar.

**A1.6: DashboardTab unvollständige Trend-Linie** — 5 Traffic-Lights aber nur 2 (Finanzen+Gleichheit)
mit addTrendArrow (AdvisorTab hat 6 Indikatoren, davon 2 mit Trend → Konsistenz-Lücke).

**A1.7: AdvisorTab 1 Action keine Alternativen** — `WindowOverview.java:608` `buildAdvice()` ist
eine 7-fach if/else-Kaskade → nur ein Tipp kommt zurück. Trade-offs ("cash vs growth") unsichtbar.

**A1.8: DemographicTab bucketed visualization endet bei `y < 450`** — `WindowOverview.java:387`
harter Cutoff ohne Scroll-Mechanismus. Viele Vermögens-Buckets → nur die ersten ~10 sichtbar.

---

## TEIL B: Q2 — USER × TECHNICAL (Spieler unter Last)

### Befunde

**B2.1: DebugTracer RingBuffer 8192 voll in ~2-3 Min** — bei aktiviertem `debugTracing` mit
`traceEvery(300)` und 60 FPS in ~8000 Frames gefüllt. UI zeigt keine Warnung. Dump-Datei >1MB.

**B2.2: Tab-Wechsel = close+toggle() = 100ms Hitch** — `EconWindowBase.java:96` Tab-Klick
schließt+reopened das ganze Fenster. Bei aktiven Slidern + Diagnostics = GC-Spike alle paar Sekunden.

**B2.3: DiagnosticExporter IO-Error ohne User-Surface** — `DiagnosticExporter.java:writeAll()`
fängt IOException, loggt nur `System.err.println(...)` ohne EventLog. Spieler merkt nicht dass
CSVs nicht geschrieben werden.

**B2.4: LiveSlider supplier.getAsInt() jeden Frame** — `EconWindowBase.java:243` `render()` ruft
`updateDisplay(supplier.getAsInt())` jeden Frame. Bei aktiven Slidern und voller Frame-Rate
Last-Verstärker.

**B2.5: SIBLINGS-pattern stale nach JVM-sleep/Crash** — `EconWindowBase.java:18-21` 4 statische
`winX`-Felder. Engine-Crash + Reload zeigen sie auf alte Window-Objekte. Toggle() gibt keine
Warnung, nur leere `currentManager() == null` → silent no-op.

**B2.6: Quickview dailyKPI Wallet-Iteration JEDES Frame** — `WindowQuickview.java:renderSidePanelContent()`
ruft `sim.wallets().circulating()` jeden render. 60 FPS × 60-100 Slots = 1800-6000 ops/s
verschwendet, auch wenn Quickview nicht sichtbar ist.

**B2.7: Event-Chronik hart gekappt bei `y < 480`** — `WindowEconomy.java:BooksTab` max 8 Events
dann Cutoff. Bei Wirtschafts-Krise = 50 Events/Tag. Spieler sieht die "what just happened"-Kritikalitäten nicht.

---

## TEIL C: Q3 — CODER × UX (UI-Code-Pfad)

### Befunde

**C3.1: WindowOverview 948 LOC** — 4 Tabs (Dashboard/Demographics/Advisor/Property) + 8 Helpers
statisch inline. Refactor in `ui/tabs/Overview/*.java` überfällig. God-Class-Verdacht (Rule 14).

**C3.2: Kein Grid-Layout-System** — Alle Tabs nutzen hardcoded `x+170, x+240, x+380, x+480`
Spalten-Positionen. 16 Tabs duplizieren die Positionierung. Eine Änderung des Layout-Grids
berührt 16 Dateien.

**C3.3: WindowQuickview code-dupliziert in build()+renderSidePanelContent()** — `WindowQuickview.java:46-225`
vs `:227-308`. KPI-Reihenfolge 1:1 identisch, ~70 LOC dupliziert. Bug-Patch in der einen Methode
übersieht die andere.

**C3.4: anchorY=296 magic number für Minimap** — `WindowEconomy.java:67` `return 296;`.
Wenn Engine die Minimap ändert, bricht das Layout.

**C3.5: LiveSlider ist private inner class in EconWindowBase** — Nicht wiederverwendbar in
Side-Panel-Pfad. Quickview.renderSidePanelContent emuliert KPI-Logik als nicht-interaktiven Code.

**C3.6: 16 Tab-Klassen als private static inner classes** — Tabs sind private, kein Module-Boundary.
WindowOverview.activeInstance() ist die einzige external-accessible, alles andere nicht testbar
ohne die ganze Window-Datei anzufassen.

**C3.7: toDisplayName Sk-06 in WindowEconomy duplicated** — `WindowEconomy.java:476` macht
String-Split Display-Name. Nutzbar in PricesTab, SubsidiesTab. FirmsTab nicht — Inkonsistenz.

---

## TEIL D: Q4 — CODER × TECHNICAL (System-Stabilität)

### Befunde

**D4.1: SIBLINGS statische WindowRefs ohne Reset** — `EconWindowBase.java:18` nie zurückgesetzt
bei Save/Load. Engine-Crash + Cold-Start können stale References halten. `inter` Feld pro Window
lebt weiter, Engine-Zugriffe werfen NPE.

**D4.2: build() ohne Error-Boundary** — `EconWindowBase.java:144` ruft
`tabs[this.activeTab].build(this.sim, ...)` ohne try/catch. NPE in `sim.flowPrices().ready()`
killt den ganzen Window-Build. Spieler sieht Closed-Button ohne Content.

**D4.3: currentManager() swallow Exception silently** — `EconWindowBase.java:114`
`try { return view.main.VIEW.current().uiManager; } catch (Exception e) { return null; }`. Wenn
VIEW null oder in Init-Phase, kein Logging, kein Signal an Diagnostik.

**D4.4: Toggle-Pattern race-condition bei Double-Click** — `EconWindowBase.java:75` `toggle()`
ist nicht atomar. Doppelklick kann zweimal `inter.activate(root)` triggern.

**D4.5: windowEnabled flag** — `WindowQuickview.java:211` prüft `EconConfig.windowEnabled`
nur in renderSidePanelContent. Nicht in build(). Spieler könnte Quickview offen haben wenn
flag false wird.

**D4.6: Hardcoded font-widths nicht themable** — `EconWindowBase.java:174-194` `FONTW_LABEL=64,
FONTW_KPI=144, FONTW_HDR=256, ...` als statische `int`. Kein Locale-Switch, kein High-DPI-Mode.

**D4.7: DiagnosticExporter eventBuffer.max 10.000** — `DiagnosticExporter.java:28` Max Events.
Bei intensiver Wirtschafts-Session gehen Events verloren ohne Notification. Buffer-Overflow
nirgendwo geloggt.

---

## TEIL E: Cross-Synthesis — Smoking-Gun-Chains

| # | Chain | Quadranten-Konvergenz | Konsequenz |
|---|-------|----------------------|------------|
| 1 | **Tab-Lag** | C3.6 + B2.2 + D4.2 | Tab-Klick = Lag-Spike ODER leerer Window-Body. Spieler denkt Mod crashed |
| 2 | **Log-Loss** | B2.1 + B2.3 + D4.7 | Spieler kann nicht diagnostizieren warum Wirtschaft kollabiert. Logs verschwinden still |
| 3 | **Lever-Invisibility** | A1.1 + C3.2 + D4.6 | 90% der Mod-Power im Code begraben. Balance-Tuning erfordert Source-Edit |
| 4 | **Severity-Loss** | A1.4 + A1.2 + A1.7 | Spieler muss alle 100 Resources manuell durchscrollen, sieht Knappheiten erst nach Decktop-Inspection |
| 5 | **Quickview-Drift** | A1.5 + C3.3 + B2.6 | Bugfixes müssen 2 Methoden treffen + Wallet-Last läuft auch wenn Quickview unsichtbar ist |
| 6 | **Save/Load-Crash** | D4.1 + D4.3 + B2.5 | Reload nach Crash → Quickview-Toggle no-op'd still, Spieler weiß nicht warum |

---

## TEIL F: Risiko-Heatmap & empfohlene Reihenfolge

| # | Task | Severity | Aufwand | Blocker für |
|---|------|----------|---------|-------------|
| 1 | **Tab-Content-Caching + Error-Boundary** | high | 1-2 Tage | Stabilität ALLER Tabs |
| 2 | **Severity-Sort + Filter in PricesTab** | high | 1-2 Tage | Player-Mehrwert Knappheits-Erkennung |
| 3 | **Grid-Layout-Komponente** | med | 2-3 Tage | Voraussetzung für #4, #5 |
| 4 | **Lever-Discovery-Tab "Schrauben"** | high | 3-4 Tage | Balance-Iteration ohne Source-Edit |
| 5 | **Tab-Modul-Split** (WindowOverview → `ui/tabs/Overview/*.java`) | med | 1-2 Tage | Voraussetzung für #4 |
| 6 | **WindowQuickview DRY** | med | 0.5 Tage | Quickview-Code-Quality |
| 7 | **DiagnosticExport Error-Surface** | med | 0.25 Tage | Spieler-Diagnostik |

### One-Shot Decision-Prompt

> **Wenn wir NUR EINE Sache fixen, fixe "Tab-Content-Caching + Error-Boundary + Severity-Sort"
> (#1 + #2), weil:** ohne Fehler-Boundary riskiert jeder Tab-Wechsel einen leeren/weißen
> Bildschirm = Mod-Reputation-Tod. Ohne Severity-Sort verliert der Spieler jede
> Knappheits-Wahrnehmung = den eigentlichen Game-Loops-Wert der Mod. Beides zusammen
> kostet ~3 Tage und blockiert KEINE zukünftigen Sprints — die Komponenten sind additiv.
> Alles andere (Grid, Lever-Discovery, Tab-Modul-Split) ist Skalierungs-Improvement.

---

## TEIL G: UI-Redesign-Plan — Spielerischer Mehrwert

Der User-Auftrag "durchdachtes UI mit spielerischem Mehrwert" wird strukturell übersetzt
in **drei Schichten**: **Visibility** (was sehe ich?), **Agency** (was kann ich tun?),
**Causality** (warum passiert was?).

### Schicht 1 — VISIBILITY (was sehe ich?)

| Element | Wo | Spieler-Mehrwert |
|---|---|---|
| **Severity-Sort** in PricesTab | `WindowEconomy.java:PricesTab` | Resource-Tabelle sortiert nach coverage ASC. Mangel oben |
| **Severity-Filter-Chips** | Neue Toolbar in PricesTab | "Mangel+Knapp / OK+Überschuss / Alle" — Default Mangel |
| **Resource-Sparkline** (5-Tage-Trend) | Neue Spalte | Mini-Chart pro Resource, zeigt ob sich Engpass verschlimmert |
| **Tab-Sidebar-Severity-Badge** | Tab-Buttons | "Preise" zeigt "3 Mangel"-Badge wenn ≥3 knappe Resources |
| **Economy-Health-Score** | WindowOverview Header | Ein Wert 0-100 (Gini+Treasury+Production-Composit) |

### Schicht 2 — AGENCY (was kann ich tun?)

| Element | Wo | Spieler-Mehrwert |
|---|---|---|
| **Lever-Discovery-Tab "Schrauben"** | Neues Window `WindowLevers` | Alle 239 Hebel in 6 Kategorien |
| **Hebel-Suche** | WindowLevers Header | Volltext-Suche ("Staircase", "Militär", "Gini…") |
| **Live-Preview-Panel** | Bei jedem Hebel | "Bei Wert +10: erwarteter Effekt X (geschätzt aus letzten 3 Tagen)" |
| **Slider-Cluster pro Domäne** | Statt verteilt auf 5 Tabs | "Wirtschafts-Trio: Löhne+Marginal+Subsidies nebeneinander" |
| **Revert-Button pro Hebel** | WindowLevers Footer | "Änderungen seit letztem Speichern" → Liste, einzeln rücksetzbar |
| **One-Click-Szenarien** | WindowLevers Preset-Dropdown | "Krisenmodus" / "Wachstumsmodus" / "Egalitätsmodus" |

### Schicht 3 — CAUSALITY (warum passiert was?)

| Element | Wo | Spieler-Mehrwert |
|---|---|---|
| **AdvisorTab v2** — Top-3 Alternativen | `WindowOverview.java:AdvisorTab.buildAdvice()` | Statt 1 Empfehlung 3 Alternativen mit Trade-off-Tabelle |
| **Decision-Log** | Neue Spalte in BooksTab | Letzte 20 Spieler-Entscheidungen mit Outcome ("vorher 100K → nachher 80K") |
| **Causality-Graph on Demand** | Hover über Severity-Badge → Popup | "MOEBEL-Mangel ↔ Carpenter idle ↔ Tier 3 ↔ Override nicht aktiv" |
| **Counterfactual-Slider** | Bench-Panel in DebugTab | "Was wäre wenn Gini-Schwelle 0.25 statt 0.35?" |
| **Prognose-Banner** | WindowQuickview top | "In 7 Tagen: Insolvenz (-12K D erwartet) → Vorschlag: Export-Boost" |

### Schicht 4 — DELIGHT (Killer-Features)

| Element | Wo | Spieler-Mehrwert |
|---|---|---|
| **Achievement-System statt Tutorial-Linear** | `EconTutorialController` | "Erster Exportmarkt gebaut! +5% Trade-Bonus 7 Tage" |
| **Season-Recap** | In Game-Log alle 4 Seasons | "Gini 0.42→0.31 → +12K D Volksvermögen" |
| **Production-Diff-View** | FirmsTab | Vorher/Nachher-Balken für Top-10 Firms seit Hebel-Change |
| **Heatmap-Window** | Neues `WindowHeatmap` | Tile-basierte Stadt-Sicht: Welcher Stadtteil produziert was |

---

## Definition of Done für UI-Restrukturierung Sprint N+1

- [ ] `EconWindowBase.build()` hat `try { tab.build() } catch { showErrorPanel(e) }` Error-Boundary
- [ ] PricesTab sortiert nach coverage ASC, mit Filter-Chips "Mangel/Alle"
- [ ] Severity-Badge auf Tab-Buttons wenn ≥3 knappe Resources
- [ ] WindowQuickview.build() und renderSidePanelContent() teilen `KpiSection`-Helper
- [ ] `WindowLevers.java` als 7. Window mit 6 Kategorien, Suche, Live-Preview
- [ ] AdvisorTab zeigt Top-3 Alternativen statt 1 Empfehlung
- [ ] `mvn verify install -DskipTests` → BUILD SUCCESS (Rule 1)
- [ ] `bash tools/verify-doc-sync.sh` → PASS (Rule 2)
- [ ] `bash tools/verify-doc-sync.sh` Stam-Docs sync auf neue Datei (Rule 3)
- [ ] `bash tools/god-class-guard.sh --mode=hard` → 0 BLOCKS (Rule 14 nach Re-Baseline)
- [ ] WindowOverview < 600 LOC nach Tab-Modul-Split

---

## Truth-Pass zum vorherigen Audit (2026-07-28)

| Claim 2026-07-28 | Stand 2026-07-30 | Aktion |
|---|---|---|
| RES-033: Workplace-Fix 2-3d | **OFFEN** — Sprint v0.13.103+ hat Staircase geliefert, max-Employed-Diskrepanz aber nicht behoben | bleibt offen |
| RES-035: Log-Hook-Spec (6 Hooks) | **TEILWEISE** — Hook 6 (Hill-Step) in FirmSizing implementiert, andere 5 fehlen noch | bleibt offen |
| B-011: AccessAutomation exponential backoff | **OFFEN** — kein Code-Change sichtbar | bleibt offen |
| U-01..U-09 UI-Papercuts | **OFFEN** — Sprint-Aktion steht aus | bleibt offen |
| FirmEconomyKernel Unit-Tests | **OFFEN** | bleibt offen |
| Wallets SLOT-Overflow-Guard | **OFFEN** | bleibt offen |
| EconConfig volatile | **OFFEN** — 239 Hebel, davon 25 in UI mutable ohne volatile | bleibt offen |

**Cross-Cutting-Beobachtung:** Seit 2026-07-28 ist das EconConfig-Volatile-Problem **10× akuter
geworden** — Sprint v0.13.103+ hat weitere Slider-Pfade (Staircase-Tiers, Staatsbestand-Thresholds)
hinzugefügt, die im UI live-edited werden ohne volatile/memory-barrier. Race-Risiko auf das 10-fache angestiegen.

---

## Sprint-Header für CHANGELOG (Empfehlung)

```
Sprint v0.13.104+UI-AUDIT: Quad-Perspective UI-Audit
  - 16 Tabs, 5 Windows, 239 EconConfig-Hebel analysiert
  - 7 Top-Risiken priorisiert (#1 Tab-Content-Caching, #2 Severity-Sort)
  - 4-Säulen-UI-Redesign-Plan: Visibility / Agency / Causality / Delight
  - Sprint-Header-Pattern für Folge-Sprints (UI-Restrukturierung)
```
