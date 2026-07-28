# SyxEconomyMod — Quad-Perspective Audit

> **Stand:** 2026-07-28 | **Basis:** v0.13.64 (Live-Test), V71.44 Engine
> **Methodik:** 4-Quadrate (USER×UX, USER×TECH, CODER×UX, CODER×TECH)
> **Fokus:** Strukturelle Schwächen vor Phase-N — Workplace-Allocation, Logging, God-Class-Reste
> **Exclusions:** Einzel-Bug-Fixes (U-01..U-09 sind papercuts, kein strukturelles Audit-Thema)

---

## Executive Summary — Top-N Risiken VOR Workplace-Fix (RES-033/035)

| # | Risiko | Severity | LOC-Impact | Phase-N-Blocker |
|---|--------|----------|------------|-----------------|
| 1 | **Workplace-Allocation blind** — FirmLedger(850 LOC), LaborMarket(130), FirmSizing(150) haben 0 Logs im gesamten Allocation-Pfad. RES-033 (6 vs 45 Arbeiter) kann nicht verifiziert werden. | **crit** | ±6 Hooks + 1 CSV-Spalte | **yes** |
| 2 | **FirmEconomyKernel** — God-Class mit Hill-Climber + profit + split + priority. Kein Unit-Test für Hill-Step-Logik. Allocation-Fehler (RES-033) könnten HIER entstehen. | **high** | ~300 LOC | yes |
| 3 | **AccessAutomation static leak** — `accessDetectionDisabled` ist static, B-011 fix (RECOVERY_INTERVAL) ist dringend. | **high** | ~15 LOC | yes |
| 4 | **EconomySim 850+ LOC** — Nach M-1 Extraktion immer noch God-Klasse mit 45+ Fields, 50+ Accessors, 20+ Delegations. Kein Interface-Contract zwischen Subsystemen. | **med** | ±200 LOC (Refactor) | no |
| 5 | **Wallets SLOTS=60000 hardgecoded** — Kein Config-Switch. Bei >60K Bürgern → ArrayIndexOutOfBoundsException. | **med** | ~5 LOC | no |
| 6 | **IdentityMapRegistry** — `clearOnLoad()` cleart ALLE registrierten Maps. Bei Load-Fehler → Datenverlust ohne Recovery-Path. | **med** | ~20 LOC | no |
| 7 | **FlowMeter.IdentityHashMap** — `firms` Map wird auf Engine-Objekten gebaut. Nach Load → Referenz-Identität weg → firms.clear() in ensureCapacity() bei erstem tick. | **med** | ~10 LOC | no |

---

## TEIL A: Q1 — USER × UX (Spieler-Erleben)

### Befunde

**A1.1: Quickview zeigt -19M D statt -1.9M D (U-01)**
`WindowQuickview.java:53` — `CompactNumber.format(treasury)` produziert `-19M D` bei treasury = -1.9M. Das ist ein Anzeige-Bug, kein Daten-Bug. Der Spieler sieht ein falsches Ergebnis.

**A1.2: GText-Overflow im Kopfsteuer-Feld (U-02)**
`EconWindowBase.java:119` — `FONTW_SLVAL = 96` ist nach Fix von 80→96. Bei Werten wie `####-500D#` reicht 96 Zeichen immer noch nicht.

**A1.3: Firmen-Tab zeigt Rohkeys statt Namen (U-04)**
`WindowEconomy.java:192` — `f.blueprint()` liefert z.B. `FARM_GRAIN` statt `Farm Getreide`. `toDisplayName()` existiert für Ressourcen, aber NICHT für Blueprint-Keys.

**A1.4: Bücher-Tab Sanity-Check zeigt "Kasse + Umlauf = 203.3K D" bei -1.8M Treasury (U-09)**
`WindowEconomy.java:290` — `treasury + circulating` als Sanity-Check ignoriert: Escrow-Guthaben, Property-Shares, Pending-Messages, und den Unterschied zwischen `FACTIONS.player().credits()` (double) und Wallets (int).

**A1.5: Kein Workplace-Tab / Employment-Insight**
Es gibt keinen Tab der zeigt: Wie viele Arbeiter hat jedes Workplace? Wie viele SOLL es haben? Die Firma zeigt `employed` und `target`, aber NICHT `max`. Der Spieler sieht die Diskrepanz nicht.

**A1.6: Demografie-Tabelle leer (U-03)**
`CitizenClass.render()` zeigt 0 Zeilen bei 37 Siedlern. Vermutlich: `CitizenClass.classifiedCountInternal()` = 0 weil `classifyAll()` erst nach Median-Refresh läuft.

### Empfehlungen
- A1.1: `CompactNumber.format()` muss negative Werte korrekt handhaben
- A1.5: Workplace-Tab mit employed/max/discrepancy-Ratio (braucht RES-035 Hook + max_capacity CSV-Spalte)

---

## TEIL B: Q2 — USER × TECHNICAL (Spieler unter Last)

### Befunde

**B2.1: Treasure-Crisis-Stufen ohne visuelles Feedback**
`TreasuryCrisis.java:1-120` — 6 eskalierende Stufen (0-5) mit konkreten Maßnahmen, aber KEIN UI-Hinweis. Der Spieler sieht nur "Staatskasse: -1.8M D" ohne zu wissen dass ALLE Löhne halbiert, GrainDole deaktiviert, etc. ist.

**B2.2: AccessAutomation deaktiviert sich permanent bei erster Exception (B-011)**
`AccessAutomation.java:29` — `accessDetectionDisabled = true` bei erster Exception. Fix existiert (RECOVERY_INTERVAL = 1800 Ticks), aber:
- Nach Recovery → erneute Exception → erneut deaktiviert
- Kein logarithmischer Backoff
- Housing-Einrichtungsziele sind dann permanent auf 0

**B2.3: Log-Flood bei Trace-Dumping**
`DebugTracer.java:120` — `dump()` schreibt 8192 Events in eine Datei. Bei aktiviertem `debugTracing` mit `every(300)` Sampling füllt sich der Buffer in ~8000 Frames (~2.5 Minuten bei 60 FPS). Dump-Datei kann >1MB werden.

**B2.4: Emergency-Diagnostic-Export bei Crash**
`DiagnosticExporter.java:143` — `exportDay()` schreibt async auf Background-Thread. Bei JVM-Crash → max. ein paar hundert Bytes im Executor-Buffer. ABER: `eventBuffer` (DC-01 SummaryEvents) wird nur bei `flush()` (Save) geschrieben. Bei Crash → alle SummaryEvents weg.

**B2.5: Wallets circulating() iteriert 60K Slots**
`Wallets.java:380` — `circulating()` iteriert `ownedCount` (typisch 30-100 Slots). OK bei <1000 Bürgern. Aber: Quickview ruft `circulating()` JEDES Frame auf → 30-100 Iterationen pro Frame bei 60 FPS = 1800-6000 Operationen/Sek. Nicht kritisch, aber verschwendet CPU.

### Empfehlungen
- B2.1: UI-Traffic-Light für TreasuryCrisis-Tier (z.B. Rotes Auge bei Tier ≥3)
- B2.2: Exponential Backoff statt feste RECOVERY_INTERVAL
- B2.4: `eventBuffer.flush()` auch bei `DebugTracer.dump()` aufrufen

---

## TEIL C: Q3 — CODER × UX (UI-Code-Pfad)

### Befunde

**C3.1: EconWindowBase — Tab-Rebuild bei jedem Klick**
`EconWindowBase.java:130-145` — Tab-Click schließt und öffnet das ganze Fenster neu (`close(); toggle();`). Das ist ~100ms Lagspike pro Tab-Wechsel. Besser: Tab-Content nur neu rendern, nicht das ganze Panel.

**C3.2: Static Window-References (SIBLINGS-Pattern)**
`EconWindowBase.java:18-20` — `winOverview`, `winEconomy`, `winState` sind static. `InstanceScript` initialisiert sie einmalig. Bei Save/Load → statische Referenzen bleiben erhalten → kein Memory-Leak, aber auch kein Fresh-Start.

**C3.3: WindowEconomy — 6 Tabs × ~100 LOC = ~600 LOC inline**
`WindowEconomy.java` — Jeder Tab ist eine `private static final class implements TabContent`. Gute Struktur, ABER: Kein Shared-Tab-Component. FirmsTab und WagesTab duplizieren `ledger.firmFinancialSnapshots()` Aufruf.

**C3.4: FirmsTab zeigt NICHT max_capacity**
`WindowEconomy.java:195-220` — Tab zeigt `employees` und `target`, aber NICHT `max`. Der Spieler sieht die Diskrepanz nicht. (Verbindet sich mit Q1-A1.5 und RES-033.)

**C3.5: BooksTab Sanity-Check ist mathematisch falsch**
`WindowEconomy.java:290` — `treasury + circulating` als Sanity-Check ignoriert: Escrow-Guthaben, Property-Shares, Pending-Messages, und den Unterschied zwischen `FACTIONS.player().credits()` (double) und Wallets (int).

**C3.6: WindowQuickview — Kein "Mehr..." Link zum Workplace-Tab**
`WindowQuickview.java` — Quickview zeigt KPIs aber keinen Schnellzugriff auf den Workplace-Tab. Der Spieler muss manuell navigieren.

### Empfehlungen
- C3.1: Tab-Content-Caching (nur bei Tab-Wechsel neu rendern, nicht bei jedem Klick)
- C3.4: max_capacity in FirmsTab + Quickview
- C3.5: Sanity-Check auf `treasury + circulating + escrow + propertyShares ≈ 0`

---

## TEIL D: Q4 — CODER × TECHNICAL (System-Stabilität)

### Befunde

**D4.1: Wallets SLOT-MASK = 0x3FFFF (60000 Slots) — Hardcoded**
`Wallets.java:15` — `SLOTS = 60000`, `slotOf(h) = h.id() & 0x3FFFF`. Bei >60K Bürgern → Slot-Kollision. Kein Overflow-Guard. (Songs of Syx kann >100K Bürger haben.)

**D4.2: FlowMeter IdentityHashMap auf Engine-Objekten**
`FlowMeter.java:19` — `Map<RoomInstance, FirmState> firms = new IdentityHashMap<>()`. Nach Save/Load → Engine instanziiert NEUE RoomInstance-Objekte → Referenz-Identität weg → firms wird bei nächstem `sample()` komplett neu gebaut. Das ist OK (Reset), aber: `IdentityMapRegistry.register("FlowMeter", "firms", firms)` cleart die Map bei Load → `ensureCapacity()` realloc → GC-Druck.

**D4.3: EconomySim.update() — ReentryGuard ohne Timeout**
`EconomySim.java:250` — `ReentryGuard.tryEnter()` verhindert parallele Aufrufe. ABER: Kein Timeout. Wenn `update()` einmal hängt (z.B. in `warehouseMarket.buy()`), wird KEIN nächster Tick ausgeführt. Das Spiel friert ein.

**D4.4: DiagnosticExporter — async IO ohne Error-Propagation**
`DiagnosticExporter.java:156` — `IO.submit(() -> writeAll(...))`. Bei IOException → `System.err.println()` — kein EventLog, kein User-Feedback. Der Spieler sieht nicht dass Diagnostics nicht geschrieben wurden.

**D4.5: TreasuryCrisis — static State ohne Thread-Safety**
`TreasuryCrisis.java:50-60` — `activeTier`, `wagesHalved`, `taxesHiked` etc. sind alle `private static`. Kein `volatile`, kein `AtomicReference`. Bei multi-threaded Tick (vanilla nutzt keinen, aber Mod-Theorie) → Race-Condition.

**D4.6: RoomAccessImpl — failedMethods ist synchronisiert, aber canAccess nicht**
`RoomAccessImpl.java:45-47` — `failedMethods = Collections.synchronizedSet(new HashSet<>())` ist OK. ABER: `canAccess()` liest `EngineLevers.engineMirrorEnabled` (static, nicht volatile) → kann veralteten Wert lesen.

**D4.7: WarehouseMarket.save() — kein Header-Checksum**
`WarehouseMarket.java:220+` — Save-Format V8 ohne Checksum. Bei korruptem Save → `load()` liest garbage → potentielle Exception oder stille Datenkorruption.

**D4.8: EconConfig — public static mutable fields**
`EconConfig.java` — 30+ `public static` Felder die während des Spiels geändert werden. Kein `volatile`, kein Thread-Safety. UI-Slider ändern Werte die im Main-Tick gelesen werden. Im vanilla Snake2D single-threaded OK, aber fragile.

### Empfehlungen
- D4.1: Wallets SLOTS auf `Math.max(60000, maxCitizens * 2)` dynamisieren
- D4.3: ReentryGuard mit Timeout (z.B. 5 Sekunden) + Force-Reset
- D4.5: TreasuryCrisis statics → volatile oder instance fields
- D4.8: EconConfig → `volatile` für alle Slider-gesteuerten Felder

---

## TEIL E: Risiko-Heatmap & empfohlene Reihenfolge

### Phase-N → Phase-N+1 Sequencing

| # | Task | Severity | Aufwand | Blocker für |
|---|------|----------|---------|-------------|
| 1 | **RES-035: Log-Hook-Spec** (6 Hooks + CSV-Spalte) | crit | 0.5d | RES-033 Verifikation |
| 2 | **RES-033: Workplace-Fix** (nach Log-Hooks) | crit | 2-3d | Player-Experience |
| 3 | **B-011: AccessAutomation exponential backoff** | high | 0.5d | Housing-Stabilität |
| 4 | **U-01: CompactNumber negative Werte** | high | 0.25d | UI-Korrektheit |
| 5 | **FirmEconomyKernel Unit-Tests** | high | 1d | Allocation-Sicherheit |
| 6 | **Wallets SLOT-Overflow-Guard** | med | 0.5d | Große Siedlungen |
| 7 | **EconConfig volatile** | med | 0.25d | Thread-Safety |
| 8 | **Tab-Rebuild-Optimierung** | med | 0.5d | UI-Performance |

### One-Shot Decision-Prompt

> **Wenn wir NUR EIN Ding fixen, fixe RES-035 (Log-Hook-Spec) + RES-033 (Workplace-Fix), weil:**
> Ohne Logs können wir die 6-vs-45-Arbeiter-Diskrepanz nicht messen. Ohne den Fix bleibt das Haupt-Gameplay-Problem (Workplaces funktionieren nicht) ungelöst. Beides zusammen kostet ~3 Tage und blockiert ALLE folgenden Phasen. Alles andere ist kosmetisch dagegen.

---

## Definition of Done für Phase N+1

- [ ] RES-035: 6 Log-Hooks implementiert via `LoggingAdapter.csvTrace`
- [ ] RES-035: `FIRM_HEADER` um `max_capacity` (15. Spalte) erweitert
- [ ] RES-035: `LoggingAdapter.Category.WORKPLACE` Konstante hinzugefügt
- [ ] RES-033: Workplace-Allocation zeigt employed=45 statt employed=6
- [ ] B-011: AccessAutomation exponential backoff implementiert
- [ ] U-01: `CompactNumber.format()` korrekt für negative Werte
- [ ] `mvn compile` → BUILD SUCCESS
- [ ] `grep -c "EventLog.log\|csvTrace" FirmLedger.java` → ≥5 (vorher: 1)
- [ ] `grep -c "EventLog.log\|csvTrace" LaborMarket.java` → ≥2 (vorher: 0)
