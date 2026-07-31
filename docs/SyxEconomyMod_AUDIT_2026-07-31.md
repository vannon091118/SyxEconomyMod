# SyxEconomyMod — Pre-M-UI-2 WindowLevers Readiness Audit

> **Stand:** 2026-07-31 | **Basis:** v0.13.101 (HEAD = `66d8069` post Sprint v0.13.124+ Hotfix-2),
> **commit-chain:** `e667436` v0.13.116+ Hotfix → `ae099c3` v0.13.103+ Staircase-Body → `de8e4df` v0.13.123+M-UI-1.1 Polishing → `66d8069` v0.13.124+ Hotfix-2
> **Methodik:** 4-Quadrate (USER×UX, USER×TECH, CODER×UX, CODER×TECH)
> **Fokus:** Strukturelle Schwächen **vor** Sprint M-UI-2 WindowLevers (7. Fenster mit 239 EconConfig-Hebel + Scenario-State-Machine + Live-Preview)
> **Exclusions:** UI-Polishing-Sprints (M-UI-1.1), Mockito-blocker-Fix (separater Sprint v0.13.124+M-UI-1.2), Tab-Modul-Split WindowOverview (M-UI-3 done)

**Cross-Reference:** Truth-Pass mit `docs/SyxEconomyMod_AUDIT_2026-07-28.md` (v0.13.64-Audit). Findings aus 2026-07-28 sind in **TEIL A–D Status-Spalten** explizit als `closed` / `partial` / `open` markiert; nur wirklich neue Funde landen hier im Hauptteil.

---

## Executive Summary — Top-N Risiken VOR Sprint M-UI-2 WindowLevers

| # | Risiko | Severity | LOC-Impact | Pre-M-UI-2-Blocker |
|---|--------|----------|------------|-------------------|
| 1 | **D4.8 EconConfig public static mutable fields ohne `volatile`** — WindowLevers manipuliert 239 Werte via Live-Preview-Slider; ohne Thread-Safety sind Race-Conditions im Main-Tick stille Datenkorruption | **high** | ±30 LOC | **YES** |
| 2 | **Mockito-inline Java 21 CDS-Blocker** — 24/24 KpiSectionTest blockiert (Sprint v0.13.123+ dokumentiert). M-UI-2 mit Scenario-State-Machine kann ohne Test-Infrastruktur nicht regression-safe gebaut werden | **high** | ~5–8 LOC pom.xml | **YES** |
| 3 | **C3.1 ECON-Window-Baseline Tab-Rebuild-Architecture** — `EconWindowBase.close(); toggle();` bei Tab-Click = ~100ms Lagspike. Mit 239 Hebeln + 6 Kategorien wird das zur Performance-Katastrophe | **med** | ±100 LOC Refactor | **YES** |
| 4 | **WindowState.java=612 SLOC + WindowEconomy.java=486 SLOC (parse_metrics-verified, Rule-6-exempt)** — beide noch mit inline-Tabs (je 6 `static final class`) obwohl Rule-6 + WindowOverview-Tab-Split-Pattern (M-UI-3) verfügbar sind. M-UI-2 folgt demselben Split-Pattern | **med** | ±400 LOC Split | **suggested** |
| 5 | **A1.1 CompactNumber-Negativ + A1.2 GText-Overflow** — slider-getriebene Live-Preview mit negativen Werten wird unbrauchbar (`-19M` statt `-1.9M`, `####-500D#` überläuft 96 char) | **med** | ~10 LOC | **NO** (stark empfohlen) |

**One-Shot Decision-Prompt:**

> **Wenn wir NUR EIN Ding fixen, fixen wir D4.8 (EconConfig `volatile`/ConfigSnapshot-Pattern)**, weil **WindowLevers 239 Hebel via Live-Preview manipuliert — ohne Thread-Safety entstehen stille Race-Conditions zwischen Slider-Thread (UI) und Tick-Thread (Engine) die das gesamte Simulations-Modell corrupt machen können**, an **0.5d Aufwand**, **blockiert alle 4 nachfolgenden M-UI-2-Tasks**. Alles andere ist danach Kosmetik.

---

## TEIL A: Q1 — USER × UX (Spieler-Erleben)

### Befunde

**A1.1: CompactNumber-Negativ-Anzeige** *(STATUS: open)*
`EconWindowBase.java:119` / `CompactNumber.java` — aktuelle Implementation produziert `-19M D` bei `-1.9M`. WindowLevers Live-Preview mit Werten wie `-45K D/Auftrag` (z.B. Negativ-Steuer-Effekt) wäre unbrauchbar mit kaputter Formatierung.
→ **M-UI-2 Impact:** High. 239 Hebel-Previews → 100+ negative Werte erwartbar.

**A1.2: GText FONTW_SLVAL 96 char overflow** *(STATUS: partial)*
`EconWindowBase.java:119` — `FONTW_SLVAL = 96` (war 80, in v0.13.x Refactor auf 96 angehoben). Trotzdem: Bei Slider-Werten wie `####-500D-1234.567890` overflowt das Feld. WindowLevers Slider-Köpfe müssen width-aware sein.
→ **M-UI-2 Impact:** Medium. Levers-Tabellen-Layout braucht FONTW_AUTO-CALC.

**A1.3: Blueprint-Key Rohkey ohne Display-Name** *(STATUS: unknown)*
`WindowEconomy.java:FirmsTab` — Verweis auf A1.3 aus 2026-07-28 Audit. WindowLevers wird 239 Statische Feldnamen aus `EconConfig.java` rendern müssen — `firmStaircaseEnabled`, `firmStaircaseCoverageTiers`[0] etc. — das wird eine Lesbarkeits-Katastrophe ohne `toDisplayName(label)`.
→ **M-UI-2 Impact:** CRITICAL. Ohne `EconConfig.toHumanReadable(name)` werden Hebel-Spalten unlesbar.

**A1.4: Player-Insight für 239 Hebel fehlt strukturiert** *(NEU)*
M-UI-1 hat `KpiSection.Severity` (CRITICAL/LOW/OK/SURPLUS) für Severity-Ampel eingeführt. M-UI-2 WindowLevers braucht dieselbe Severity-Klassifikation für jeden Hebel: "Ist dieser Wert im grünen Bereich?".
→ **Empfehlung:** Re-Use `KpiSection.Severity.classify(double, LebelType)` Overload als WindowLevers-interner Indikator.

**A1.5: Phenom-Sprint v0.13.106+M-UI-3 WindowOverview.Split Erfolg** *(STATUS: closed)*
`WindowOverview.java:48 SLOC (parse_metrics-verified)` — war 948 LOC vor Sprint v0.13.106+M-UI-3 Tab-Split (-95%). 4 Tab-Klassen liegen jetzt in `ui/tabs/Overview/{Dashboard,Demographics,Advisor,Property}Tab.java` als externe Composition-Shell. Player sieht weiterhin 4 Tabs mit identischem Verhalten, aber Coder hat nun eine Composition-Shell ohne God-Class-Risiko.
→ **M-UI-2 Impact:** Schablonen-Pattern verfügbar (TabContent interface, static inner class, TABS-Array).

**A1.6: 239 EconConfig-Hebel ohne Revert-Button pro Hebel** *(NEU)*
`docs/UI_WINDOW_LEVERS_SPEC.md` konzipiert Revert pro Hebel + One-Click-Szenarien (CRISIS/GROWTH/EQUALITY). Aktuell: keine Revert-Mechanik in `EconConfig.java` — Wert-Snapshot-Pattern fehlt.
→ **M-UI-2 Impact:** HIGH. Vor M-UI-2-Implementation muss `EconConfig.snapshot()` + `revert(name)` API eingeführt werden.

### Empfehlungen

- **A1.6 zuerst:** `EconConfig.snapshot()` (key→value Map) + `EconConfig.revert(String key)` API designen. Dies ist Grundlage für WindowLevers-Revert-Button UND Scenario-Pattern.
- **A1.3 + A1.4:** `EconConfig.toHumanReadable(name)` Helper + `KpiSection.Severity.classify(double, HebelType)` Overload wenn erste M-UI-2-Tasks starten.

---

## TEIL B: Q2 — USER × TECHNICAL (Spieler unter Last)

### Befunde

**B2.1: 24/24 KpiSectionTest von Mockito-Blocker betroffen** *(NEU — high)*
`Sprint v0.13.123+M-UI-1.1` dokumentiert ehrlich: `mvn test -Dtest=KpiSectionTest` schlägt mit `Could not modify all classes [java.lang.Object, …]` fehl. Java 21 CDS + ByteBuddy dynamic-agent-loading-Sperre. KpiSection.java (98 SLOC) ist BLOCKER für jede weitere Test-Regression.
→ **M-UI-2 Impact:** CRITICAL. Ohne Test-Infra sind Tooltips/Severity-Regressions in WindowLevers nicht verifizierbar. POM-Fix surefire-plugin argLine (`-XX:+EnableDynamicAgentLoading` + `-Xshare:off`).

**B2.2: Wallets SLOTS hardcoded 60000 — Engine kann > 100K Bürger** *(STATUS: open — verschärft)*
`Wallets.java:15` — `SLOTS = 60000` hardcoded. WindowLevers wird Scenario-Test "100K-Bürger-Stress" als CRISIS-Szenario anbieten. Ohne SLOT-Dynamic wären 40K Bürger Over-Budget.
→ **M-UI-2 Impact:** Medium. M-UI-2 "CRISIS-Szenario"-Preset testet extreme Bevölkerung — Wallets-Overflow würde dann latent beim Spieler crashen.

**B2.3: ECON-Window Rebuild-Lag bei Tab-Wechsel** *(STATUS: open)*
`EconWindowBase.java:130–145` — `tab.onClick()` macht `window.close(); window.toggle();` = ~100ms Lagspike. M-UI-2 WindowLevers mit 5–6 Kategorie-Tabs (Staat/Steuer/Wirtschaft/Handel/Soziales/Debug) → 5× mehr Tab-Klicks als heute.
→ **M-UI-2 Impact:** HIGH. Tab-Content-Caching Refactor (~100 LOC) muss VOR M-UI-2 fertig sein, sonst ruckeln 239-Hebel-Slider.

**B2.4: DebugTracer 8192-Event-Buffer Flood bei aktivem debugTracing** *(STATUS: unknown)*
`DebugTracer.java:120` — wenn `debugTracing=true` UND `every(300)` Sampling → Buffer voll in ~2.5 Minuten → Dump-Datei > 1MB. WindowLevers "Debug-Tab" würde diese Spalte rendern müssen. Aktuelles Sample-Rate ist nicht slidable.
→ **M-UI-2 Impact:** Low. WindowLevers-Debug-Sektion kann Sample-Rate-Slider anbieten.

**B2.5: DiagnosticExporter async IO ohne Player-Feedback** *(STATUS: unknown — 2026-07-28 audit)*
`DiagnosticExporter.java:156` — bei IOException nur `System.err.println()`, kein EventLog-Eintrag, kein User-Feedback. M-UI-2 "Live-Preview" muss visuell konsistent sein — wenn die Hebel-Werte nicht aus dem CSV ableitbar sind (wegen async-IO-Loss), wird Live-Preview-Wert vs CSV-Wert divergieren.
→ **M-UI-2 Impact:** Medium. CSV-Drift-Detection zwischen Live-Preview und Disk-Werten notwendig.

### Empfehlungen

- **B2.3 zuerst** (Tab-Content-Caching) — direkt in TEIL C als Q3 umgesetzt, hier nur als USER-TECH-Sichtbarkeit erwähnt.
- **B2.1 zweitens** (Mockito-Blocker) als Pflicht-Sprint v0.13.124+M-UI-1.2 VOR M-UI-2.
- **B2.2 drittens** (Wallets-SLOT-Dynamic) kann verschoben werden, wenn M-UI-2-Szenarien CRISIS bei <60K Bürgern bleiben.

---

## TEIL C: Q3 — CODER × UX (UI-Code-Pfad)

### Befunde

**C3.1: ECON-Windows mit inline-Tabs (kein TabContent-Split-Pattern)** *(NEU — MED)*
`WindowState.java:612 SLOC (parse_metrics-verified)` mit 6 inline-`static final class` Tabs. Jeder Tab = ~100–130 LOC. **`WindowEconomy.java:486 SLOC` (parse_metrics-verified)** mit 6 Tabs analog. **WindowState liegt am Rule-14 warn-Threshold (600 SLOC) — aber ist Rule-6-exempt (UI-Window-Sacta-Pattern per God-Class-Guard baseline-yml exemption). Das Tab-Split-Pattern aus **v0.13.106+M-UI-3** (WindowOverview 948→48 SLOC, parse_metrics-verified) ist verfügbar aber noch nicht angewendet auf WindowState/WindowEconomy.
→ **M-UI-2 Impact:** Wenn WindowLevers dieses Pattern ignoriert (z.B. inline-Tab-Klassen für 6 Kategorien × ~100 LOC = 600 LOC), dann ist es ab Tag 1 eine God-Class.

**C3.2: WindowState.DebugTab Reflection-Stub mit Empty-Catches** *(NEU — MED)*
`WindowState.java:604 / 612 / 613` — `catch (NoSuchMethodException ignored) {}` und `catch (ClassNotFoundException ignored) {}`. Empty-catches verbergen, ob die Engine-Version V71.44 oder neuer ist. M-UI-2-Revert-Pattern (das Engine-BypassGate nutzt) würde ähnliche Reflection-Paths brauchen.
→ **Empfehlung:** Empty-Catches durch `EventLog.log("DEBUG", "method not found: " + clazz.getName() + "#" + methodName)` ersetzen. Pattern dokumentieren.

**C3.3: WindowQuickview.renderSidePanelContent() = no-op Trade-Off** *(STATUS: deferred)*
`Sprint v0.13.116+ Hotfix e667436` hat Side-Panel Body auf trade-off no-op gesetzt + 2 addKpiSidePanel-Helper als Dead-Code entfernt. Out-of-Scope-Verweis auf Sprint v0.13.126+ für proper GuiSection + Holder-Pattern (Rule 15 für UI.FONT()).
→ **M-UI-2 Impact:** WindowLevers Side-Panel (Revert-Status-Display) wird von demselben Holder-Pattern profitieren — beide Sprints teilen sich die Infrastruktur.

**C3.4: KpiSection Severity.classify Policy-Fix v0.13.123+M-UI-1.1** *(STATUS: closed)*
`KpiSection.java:55` — `Double.isNaN || coverage == POSITIVE_INFINITY` returnen OK; -∞ / negative / 0.0 / <0.3 returnen CRITICAL. Tests 24/24 (Mockito-Blocker separater Sprint).
→ **M-UI-2 Impact:** Severity-Ampel in WindowLevers kann direkt `KpiSection.Severity.classify(value, HebelType.STRING)` overload nutzen — kein neuer Code nötig.

**C3.5: Player-Controller-Composition KpiSection vs WindowLevers** *(NEU — HIGH)*
`KpiSection.java` hat `addKpi(content, x, y, icon, label, value, color)`, `addSlider(content, x, y, label, getter, min, max, step, incAction, decAction)`, `addCheckbox(content, x, y, label, initial, setter)` als **public-static**. Dies ist die direkte Voraussetzung für WindowLeversTab-Composition.
→ **M-UI-2 Impact:** WindowLeversTab MUSS `KpiSection.addKpi/Slider/Checkbox` reusen — kein neuer UI-Helper-Code. Wenn aber WindowLevers 239 Hebel in einer Tabelle braucht, dann ist `addKpi` nicht das richtige Pattern (Layout-Pattern at x+170 / x+380 ist hardcoded). **NEUE-Helper nötig:** `Layout.grid(3cols, gap=10).at(x,y).kpi(...).slider(...)` (Spec aus `docs/UI_GRID_LAYOUT_SPEC.md`).

### Empfehlungen

- **C3.1 + C3.5 unmittelbar vor M-UI-2:** Tab-Modul-Split Sprint v0.13.127+ (WindowState + WindowEconomy 1:1 nach M-UI-3-Pattern) + Layout.java Implementation (Spec aus `docs/UI_GRID_LAYOUT_SPEC.md`). Beide Refactors sind atomar pro Sprint machbar (~100 LOC pro Window-Split, ~150 LOC Layout).

---

## TEIL D: Q4 — CODER × TECHNICAL (System-Stabilität)

### Befunde

**D4.1: EconConfig public static mutable — kein `volatile`** *(NEU — HIGH)*
`EconConfig.java` — **257 `public static` Felder** (parse_metrics-verified, baseline_entry sagt `loc=332 fields=256` mit drift +1/+1 — innerhalb Floor-Schutz _DRIFT_FLOOR=2). Beispiele: `public static boolean letterHotkeyFallbackEnabled`, `public static double perHeadTax`. Slider in UI = schreibender Thread; Engine = lesender Thread. Ohne `volatile` / `AtomicReference` / Snapshot-Map: Race-Condition, "Wert sprang zurück", "Wert wurde gespeichert aber nicht angewendet". M-UI-2 Live-Preview-Panel ist exakt der Use-Case der diese Lücke schmerzhaft sichtbar macht.
→ **M-UI-2 Impact:** CRITICAL. Vor M-UI-2: `EconConfig` umstellen auf `volatile` für alle 257 Felder ODER `EconConfig.snapshot()` Map-Pattern mit Copy-on-Write-Schreibschutz.

**D4.2: TreasuryCrisis 6-Tier Cascade — static State ohne volatile** *(STATUS: open — verschärft)*
`TreasuryCrisis.java:50–60` — `activeTier`, `wagesHalved`, `taxesHiked` etc. sind alle `private static`. Kein `volatile`, kein `AtomicReference`. M-UI-2 CRISIS-Szenario-Preset würde explizit diese Stufen triggern — bei Race wird die Stufe inkonsistent geschrieben.
→ **M-UI-2 Impact:** HIGH. CRISIS-Preset ist Hauptanwendungsfall dieser Stufen — sie müssen atomar gesetzt werden können.

**D4.3: ReentryGuard ohne Timeout** *(STATUS: open)*
`EconomySim.java:250` / `ReentryGuard.java` — bei hängendem `update()` blockiert der Main-Tick endlos. Kein Force-Reset. M-UI-2 Scenario-Preset würde mehrere Sub-Systeme synchron anstoßen — wenn eines hängt, friert das ganue Engine ein.
→ **M-UI-2 Impact:** Medium. Vor M-UI-2: `ReentryGuard.tryEnter(maxWaitMs=5000) → bei Timeout: force-exit + EventLog` einführen.

**D4.4: DiagnosticExporter async IO ohne Propagation** *(STATUS: open)*
`DiagnosticExporter.java:156` — bei IOException nur `System.err.println()`. WindowLevers Live-Preview-Panel liest CSV-Werte für Anzeige — wenn async-IO silently failed, divergiert die Live-Vorschau von den tatsächlichen Werten.
→ **M-UI-2 Impact:** Low (für reine UI), aber Medium (für Player-Trust).

**D4.5: ChunkedSave — kein Header-Checksum** *(STATUS: open — PRIOR audit D4.7)*
`ChunkedSave.java` — Save-Format V8 ohne Checksum. Bei korruptem Save → lautlose Datenkorruption. M-UI-2 Scenario-Preset wird in Save-State persistiert; Save-Corruption ist dann "Snapshot-State + Scenario-Preset verloren".

**D4.6: 9× `catch (Throwable)` + 11× empty-catches in benchmark/WindowState** *(NEU — LOW)*
`AdapterReflectionBenchmark.java:247-294` (Benchmark-only — Sancta-Sancta per Rule 14). `WindowState.java:604/612/613` mit `catch (NoSuchMethodException ignored) {}`. **Adapter-Production-Code:** `VanillaDiplomacyAdapter.java:60/90`, `GoodsAccessImpl.java:59` mit `catch (Throwable t)` — haben aber Logging im Body.
→ **M-UI-2 Impact:** Low. Kein direkter Effekt auf M-UI-2 Implementation.

**D4.7: Wallets IdentityHashMap<Object, X> mit Engine-Objekten als Key** *(STATUS: open — PRIOR audit D4.2)*
`FlowMeter.java:19` — `IdentityHashMap<RoomInstance, FirmState> firms`. Nach Save/Load → neue RoomInstance-Referenzen → firms komplett neu gebaut. OK (Reset), aber GC-Druck bei großen Savegames. M-UI-2 mit Live-Preview über Thronsaal-Savegames macht dieses Thema Player-sichtbar.

### Empfehlungen

- **D4.1 + D4.2 PARALLEL** als Sprint v0.13.127+ (D4.1 ~30 LOC volatile-anotation oder Snapshot-Pattern; D4.2 TreasuryCrisis atomic-stages Refator).
- **D4.3 ReentryGuard-Force-Reset** als Sprint v0.13.128+ (5 LOC, niedrig-risk).
- **D4.7** kann auf Sprint v0.13.200+ verschoben werden, da Player-Impact erst bei >10K Settlements.

---

## Root-Cause-Clusters (Cross-Synthesis Phase 2)

Per quad-perspective-audit Skill: 3 Root-Cause-Clusters aus Q1–Q4-Cross-Synthesis identifiziert:

**Cluster 1 — M-UI-2 LivePreview Race-Condition Cluster** (Severity: high, Blocker: YES)
- **Q4-relevante Findings:** D4.1 (EconConfig 257 mutable static non-volatile, parse_metrics-verified), D4.2 (TreasuryCrisis 39 `private static` fields, 0 davon volatile, parse_metrics-verified)
- **Q1-relevante Findings:** A1.4 (Severity-Ampel für Hebel-Preview benötigt), A1.6 (Revert-Button-API fehlt)
- **Q3-relevante Findings:** C3.5 (Layout fluent-API für 239-Hebel-Tabelle benötigt)
- **Smoking-Gun:** WindowLevers Live-Preview schreibt Slider-Werte zur Engine-Tick-Phase; ohne `volatile`/atomic liest Engine stale Werte; Player sieht Hebel-Slider beeinflusst nichts -> still simulation-state-corruption.
- **Master-Fix:** Sprint v0.13.127+M-UI-Foundation (D4.1 + D4.2 + A1.6 in einem Sprint).

**Cluster 2 — Test-Infrastruktur Cluster** (Severity: high, Blocker: YES)
- **Q2-relevante Findings:** B2.1 (Mockito-inline Java 21 CDS-Blocker, 24/24 KpiSectionTest down)
- **Q4-relevante Findings:** D4.4 (DiagnosticExporter async IO ohne Error-Propagation), D4.5 (ChunkedSave keine Header-Checksum)
- **Smoking-Gun:** Ohne lauffähige Mockito-Tests wird jede M-UI-2-Implementation unter Regression-Burnout leiden; Tooltips/Severity-Regressions im WindowLevers unbemerkt; CSV-Drift zwischen Live-Preview und Disk-Werten nicht detektierbar.
- **Master-Fix:** Sprint v0.13.128+M-UI-Testing (pom.xml surefire-Plugin argLine + erste WindowLeversTab-Tests inkl. CSV-Drift-Detection-Test).

**Cluster 3 — UI-Performance Cluster** (Severity: med, Blocker: YES)
- **Q3-relevante Findings:** C3.1 (ECON-Window inline-Tabs mit close()/toggle()-Rebuild = ~100ms Lagspike)
- **Q2-relevante Findings:** B2.3 (DebugTracer Buffer-Flood bei aktivem debugTracing), B2.5 (Wallets.circulating() iteriert 60K Slots)
- **Smoking-Gun:** M-UI-2 mit 239 Slidern + Live-Preview-Tab-Wechsel + ständiger Slot-Iteration -> garantierte Frame-Drops bei Player; WindowLevers-Klick auf Kategorie-Tab ware spuerbar ruckelig.
- **Master-Fix:** Sprint v0.13.129+M-UI-Infrastructure (Tab-Content-Caching + WindowState/WindowEconomy Tab-Modul-Split + Layout.java fluent-API).

## TEIL E: Risiko-Heatmap & empfohlene Reihenfolge (Phase N → Phase N+1)

### Phase-N Sequencing (3-Sprint-Pre-M-UI-2-Plan)

| Rang | Sprint-Tag | Inhalt | Severity | Aufwand | Blocker |
|---|---|---|---|---|---|
| 1 | **v0.13.127+M-UI-Foundation** | D4.1 EconConfig volatile/Snapshot + D4.2 TreasuryCrisis atomic-stages | high | 0.5–1d | **YES** für M-UI-2 |
| 2 | **v0.13.128+M-UI-Testing** | B2.1 Mockito-blocker pom.xml Fix + erste WindowLeversTab-Test | high | 0.5d | **YES** für jede Regression-Safety |
| 3 | **v0.13.129+M-UI-Infrastructure** | C3.1 WindowState+WindowEconomy Tab-Modul-Split + C3.5 Layout.java fluent-API + C3.1 Tab-Content-Caching | med | 2–3d | YES für M-UI-2 Implementation-Speed |
| 4 | **v0.13.130+M-UI-2 WindowLevers** | WindowLeversKernel (Scenario-State-Machine CRISIS/GROWTH/EQUALITY) + WindowLeversTab (6 Kategorien mit Volltext-Suche + Live-Preview + Revert-Button pro Hebel) + EconConfig.snapshot()/revert() API | high | 3–4d | n/a |
| → | Backlog | B2.2 Wallets SLOT dynamic | LOW | 0.5d | no |
| → | Backlog | A1.1 / A1.2 Data-Display-Hygiene Sprint | MED | 0.25d | no |
| → | Backlog | C3.3 WindowQuickview Side-Panel proper Rebuild | MED | 0.5d | no |
| → | Backlog | D4.3 ReentryGuard Force-Reset | MED | 0.5d | no |

### One-Shot Decision-Prompt (Top-1)

> **Wenn wir NUR EIN Ding fixen**, fixen wir **D4.1 EconConfig volatile/Snapshot-Pattern**, weil **WindowLevers 239-Hebel LivePreview ist exakt der Use-Case der eine Race-Condition zwischen UI-Slider-Thread und Engine-Tick-Thread schmerzhaft sichtbar macht — ein "Wert sprang zurück" oder "Wert wurde gespeichert aber Engine-Tick verwendet veralteten Wert" wäre die am schlechtesten reproduzierbare Bug-Klasse und korruptiert das gesamte Simulations-Modell**, an **0.5d Aufwand**, blockiert alle 4 nachfolgenden M-UI-2-Tasks.

---

## Definition of Done für Phase N+1 (v0.13.130+M-UI-2 WindowLevers)

Pre-Implementation DoD (Sprint v0.13.127+..v0.13.129+):

- [ ] **D4.1 verifiziert:** `python3 tools/god-class-guard/parse_metrics.py src/.../EconConfig.java` zeigt `fields=257` (alle entweder `volatile` oder hinter einem `EconConfig.Snapshot` Map mit Copy-on-Write)
- [ ] **D4.2 verifiziert:** `TreasuryCrisis` static fields converted to either `AtomicInteger` oder instance-state-mit-volatiler
- [ ] **B2.1 verifiziert:** `mvn test -Dskip.bump=true -Dtest=KpiSectionTest` zeigt `Tests run: 24/24` (Mockito-blocker gelöst)
- [ ] **C3.1 verifiziert (WindowState):** `python3 tools/god-class-guard/parse_metrics.py src/.../WindowState.java` zeigt `loc < 200` SLOC (Pattern aus v0.13.106+M-UI-3 angewendet, 6 Tabs in `ui/tabs/State/{Warehouses,Fiscal,PublicWorks,Social,Faith,Debug}Tab.java`)
- [ ] **C3.1 verifiziert (WindowEconomy):** `python3 tools/god-class-guard/parse_metrics.py src/.../WindowEconomy.java` zeigt `loc < 200` SLOC (analog)
- [ ] **C3.5 verifiziert:** `Layout.java` (~150 LOC fluent-API) implementiert + Mockito-freie `LayoutCoordinateTest.java` (für die Slider-Grid-Composition)

WindowLevers Implementation DoD (Sprint v0.13.130+M-UI-2):

- [ ] **WindowLeversKernel.java** mit 6 Kategorien-Scanner (Staat/Steuer/Wirtschaft/Handel/Soziales/Debug) aus `EconConfig.class.getFields()` reflection
- [ ] **WindowLeversTab.java** mit Volltext-Suche (`EconConfig.toHumanReadable(name)` Mapping), Live-Preview-Panel (`Severity.classify(value, type)`), Revert-Button pro Hebel (`EconConfig.snapshot()/revert(name)`)
- [ ] **3 Scenario-State-Presets:** CRISIS (TreasuryCrisis-Tier-5 Force), GROWTH (productionSubsidies × 2 + taxesHalved), EQUALITY (mediateGiniBelow=0.3 + allSubsidiesEnabled=true)
- [ ] Player-Facing DoD: 239 Hebel in 6 Kategorie-Tabs, Revert-State-Machine mit Undo-Stack tiefe ≥10, Scenario-Preset-Buttons in Top-Bar
- [ ] `bash tools/god-class-guard.sh --mode=hard` → 181+ PASS / 0 WARN / 0 BLOCK
- [ ] `bash tools/verify-doc-sync.sh` → PASS
- [ ] `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS
- [ ] `mvn test -Dskip.bump=true -Dtest=*WindowLevers*` → alle Tests grün
- [ ] CHANGELOG.md Sprint v0.13.130+M-UI-2 Header in Stam-Doc-Standard mit Subsummierung + Verification DoD + Out-of-Scope

---

**Dokumentenende.** True-Pass-Link: `docs/SyxEconomyMod_AUDIT_2026-07-28.md` (4-Perspektiven-Baseline-Audit vor M-UI-Chain; diese Datei ist deren Refresh nach Sprint-Chain v0.13.103+Staircase → v0.13.116+Hotfix → v0.13.123+M-UI-1.1 → v0.13.124+Hotfix-2).
