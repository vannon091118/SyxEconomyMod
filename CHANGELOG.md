# SyxEconomyMod — Changelog

> **Version:** v0.13.10 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-25
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` scheitert wenn dieser Anker driftet.

> Vollständige Historie. Die `pom.xml mod.changelog` enthält die letzten 10 Einträge als Release-Summary.
> Versionierung: 0.0.1+-Schritte (Pre-Release), kein 1.x bis zum ersten Public Release.

---

## v0.13.23 — 2026-07-25

### Phase F — AI-Adapter + Cleanup (Finale)

- VanillaAIAdapter: ClassResolver für 6 package-private Plan-Klassen
- EconConfig.useMethodHandleAdapters: gelöscht (alle Adapter auto-select)
- EconomySim: Imports bereinigt, keine MH-/Fallback-Referenzen mehr
- AdapterReflectionBenchmark: auf BypassGate-API aktualisiert
- ARCHITECTURE.md: finale Datei-Zahl 14 (10 adapter + 4 seam)

### Phase E — Boosting-Adapter auf BypassGate migriert

- VanillaBoostingAdapter: Thin-Wrapper um BypassGate + refField (GOV source-verifiziert, BOOSTABLES.java:373)
- FallbackBoostingAdapter: gelöscht — LETZTER Fallback entfernt
- EconomySim.createBoostingAdapter(): vereinfacht, kein Fallback
- ARCHITECTURE.md: 15→14

### Phase D — Warehouse-Adapter auf BypassGate migriert

- VanillaWarehouseAdapter: Thin-Wrapper um BypassGate + MethodAccessor.VoidMethod (storingSet(boolean))
- VanillaWarehouseAdapterMH: gelöscht — LETZTE MH-Variante entfernt
- FallbackWarehouseAdapter: gelöscht (BypassGate.isAvailable() ersetzt)
- EconomySim.createWarehouseAdapter(): vereinfacht, kein MH-Toggle
- ARCHITECTURE.md: 17→15

### Phase C — Transport-Adapter auf BypassGate migriert

- VanillaTransportAdapter: Thin-Wrapper um BypassGate + ClassResolver (package-private TransportInstance)
- VanillaTransportAdapterMH: gelöscht (BypassGate auto-select VarHandle)
- FallbackTransportAdapter: gelöscht (BypassGate.isAvailable() ersetzt)
- EconomySim.createTransportAdapter(): vereinfacht, kein MH-Toggle

### Phase B — Diplomacy-Adapter auf BypassGate migriert

- VanillaDiplomacyAdapter: Thin-Wrapper um BypassGate (4 Felder, auto-select VarHandle/Reflection)
- VanillaDiplomacyAdapterMH: gelöscht (BypassGate macht auto-select)
- FallbackDiplomacyAdapter: gelöscht (BypassGate.isAvailable() ersetzt)
- EconomySim.createDiplomacyAdapter(): vereinfacht, kein MH-Toggle mehr

### Phase A — Bypass-SDK (adapter/seam/)

- **BypassGate:** Zentraler Entry-Point für Private-Access-Bypasses (VarHandle/MethodHandle primär, Reflection-Fallback).
- **FieldAccessor:** Typisierte Feld-Zugriffe — IntField, DoubleField, FloatField, RefField<T>.
- **MethodAccessor:** Typisierte Methoden-Zugriffe — VoidMethod, BooleanMethod.
- **ClassResolver:** Class.forName mit Game-ClassLoader für package-private Engine-Klassen.
- **BuildStamp:** Eindeutige Build-Identität (Git-Hash, Timestamp, Dirty-Flag) in HUD und Fenstertiteln.
- **Adapter-READY-Logs:** Alle 8 Vanilla-Adapter loggen jetzt Erfolg bei Init (vorher nur Fehler).
- **Perf:** useMethodHandleAdapters=true (3–6× Speedup, Runtime Java 16+ bestätigt).

### Phase 3 Tab-Restoration — Bücher, Immobilien, Glaube

- **Bücher-Tab (WindowEconomy):** Vollständige Geldfluss-Bilanz (7 Einnahme- + 4 Ausgabekategorien), "Bücher stimmen?"-Sanity-Check (Kasse+Umlauf), EventLog-Wirtschafts-Chronik (letzte 8 Einträge).
- **Immobilien-Tab (WindowOverview):** Mieteinnahmen, Mietforderungen, Zwangsräumungen, Immobilienverkauf, Dividenden. 3 Live-Slider (Miete/Kachel, Räumung-Schwelle, Schonfrist) + 2 Checkboxen (Immobilienmarkt, Hauskauf).
- **Glaube-Tab (WindowState):** Aus SocialTab extrahiert — eigene KPIs für Religionssteuer+Liturgie, Schalter, Info-Text.
- **Preise-Tab:** +3 Spalten (Bestand, Angebot/Tag, Nachfrage/Tag) aus EconSnapshot-Daten.
- **Demografie:** 4 Wohlstandsbänder (Unterschicht→Wohlhabend) aus Histogramm-Daten.
- **Berater:** Ampel-Dashboard (5 Indikatoren), Warnketten (kausale Abhängigkeiten), Trend-Tabelle (3 Tage).

---

## v0.13.1 — 2026-07-25

### UX-Cleanup — Tooltip-Echo, Fenster-Stacking, Farbige Balken, Stack-Audit

- **Tooltip-Echo entfernt:** `hoverInfoSet` bei Tabs und Slider-Buttons gelöscht — kein "Preise"-Tooltip über dem Preise-Tab mehr.
- **Fenster-Stack-Offset:** `openCount`-Zähler + `STACK_OFFSET` (24px) — jedes neu geöffnete Fenster verschiebt sich 24px rechts+unten.
- **Defensiver Stack-Audit:** `auditStack()` zählt tatsächlich offene Fenster via `isShown()` und korrigiert `openCount` bei externem Force-Close durch Vanilla-UI-Manager.
- **Deutsche Labels:** Tab-Minimum 120→**140px**, Padding 28→**32px** — "Übersicht", "Demografie", "Soziales" nicht mehr abgeschnitten.
- **Unicode `→` → `->`:** Im Berater-Text — kein `?` mehr im Bitmap-Font.
- **Debug-Tab versteckt:** Aus `TABS`-Array entfernt, Klasse bleibt als Dev-Referenz.
- **`close()`-Reihenfolge:** `inter = null` VOR `inter.close()` + `decrementStack()` — kein Doppel-Dekrement-Risiko.
- **Farbige Balken (`coloredBar`):** ASCII-Rauten `#` in Kassen-Historie und Vermögensverteilung durch `COLOR.render()`-Rechtecke ersetzt (GOOD/SOSO/BAD).
- **EconHud-Positionierung:** `VIEW.inters().manager.viewPort()`-Kette entfernt → direkt `C.WIDTH()-200`. Icons wieder sichtbar.
- **LiveSlider-IntSupplier:** Drei Slider (Lager-Lohn, Kopfsteuer, Corvée-Aushebung) lesen Wert jeden Frame frisch — kein Snapshot-Stale mehr.

---

## v0.13.0 — 2026-07-25

### Vanilla-Nativity-Pass

- `SPanel`-Hintergrund statt selbstgezeichneter Paneele.
- `GCOLOR`-Theme gebunden.
- Vanilla-Icons + Vanilla-Checkbox + Vanilla-Slider direkt verbaut.
- `EconHud` umgestellt auf Vanilla-Iconstack — keine Bitmap-Schnitzereien mehr.

---

## v0.1.3 — 2026-07-24

### UX-Audit — 15 interaktive Tabs, Debug-Logger, SubjectJob-Overlay

- 15 interaktive Tabs (vorher 3 statische Karten im Übersichtsfenster).
- Debug-Tab: Logger opt-in/out via `EconConfig.debugLoggingEnabled`.
- SubjectJob-Overlay auf Bürger-Klick: zeigt aktuellen AI-Plan + Wirtschafts-Kontext.
- Block-Char-Balken für Ampeln (GOOD/SOSO/BAD Unicode-Rechtecke).
- Not-Liquidation-Toggle und Lohn-Slider im Staat-Fenster.
- Steuer-Toggle als Live-Slider im Finanzen-Tab.
- "Was soll ich heute tun?"-Berater mit dynamischen Prioritäten.
- Trend-Pfeile ↑↓→ auf KPIs (Gini, Treasury, Food-Days, Population).
- IdentityMapRegistry LOG-Fix — keine `System.err.println`-Spam mehr.
- TopBar entfernt — Navigation läuft jetzt über In-Window-Buttons oder Numpad-Hotkeys.
- Alle vorher statischen Toggles sind jetzt interaktiv.

---

## v0.1.2 — 2026-07-23

### Hotfix: Phantom-Profit, globales Rate-Limiting, insolvenzsichere Surplus-Share

- **Phantom-Profit-Fix:** `FlowMeter.FirmState.sample()` rechnet jetzt aus echten physischen Deltas (`producedDelta/elapsedDays`) statt aus Tageskapazität. Zimmermann zeigt Profit=0 solange er nichts produziert.
- **Globaler Rate-Limiter (AccessAutomation):** `lastErrorLogTick` und `accessDetectionDisabled` jetzt `static`. 15–20 parallele Scanner teilen sich eine Erinnerung — maximal 1× alle 100 Ticks.
- **`guildSurplusMinProfitPerWorker` = 10.0 neu:** Sockel pro Arbeiter vor Gewinnverteilung. Bäckerei (187 D/Tag) und Holzfäller (<1 D/Tag) bleiben liquid.
- **Tiered Surplus-Distribution:** `max(0, profit − workers × 10.0) × share`. Subsistenz-Betriebe zahlen nichts aus, profitable Firmen weiterhin.



---

## v0.1.1 — 2026-07-23

### Balance-Krisen-Fixes (live aus Save 35505704218901)

- **Cold-Start-Death-Spiral behoben:** `minimumWorkersPerWorkplace` 0→1. Zimmermann überlebt den ersten Tick.
- **Gewinnverteilung:** `guildSurplusShare` 0.0→0.25. Gini-Anstieg gedämpft.
- **Armutsfreigrenze:** `perHeadTaxExemptionThreshold` neu 500 D. Kopfsteuer trifft nur noch solvente Bürger.
- **Stabilität:** AccessAutomation-Erinnerungsspam eingedämmt (Ticks-Wrap-Around-sicher).

---

## v0.1.0 (Phase 4) — 2026-07-23

### Adapter-Architektur: Engine-API weggekapselt

- 5 Adapter-Interfaces: `ISyxAI`, `ISyxTransport`, `ISyxWarehouse`, `ISyxBoosting`, `ISyxDiplomacy`.
- 12 Adapter-Implementierungen (8 Vanilla + 4 Fallback). Reflection nur noch im Adapter-Konstruktor (One-Shot).
- TreasuryCrisis: **5-stufige Krisenmechanik + Hard-Floor-Verhalten in Tier 5** (vorher: 0 Treffer für `treasuryFloor`, Kasse konnte auf −900M fallen).
- DiagnosticExporter: 3 CSV/Tag (Macro 31 Spalten, Resources Long-Format, Firms). Python-Rebalance-Dashboard mit 5 Plots.
- Stage-gated Wallets (200/500/2000/5000 D nach Wirtschaftsstufe).
- `Class.forName(name, true, Humanoid.class.getClassLoader())` — der ClassLoader-Fix für `PlanOddjobber` & Co.
- 14 Adapter-Signaturen gegen `SongsOfSyx-sources.jar` verifiziert.



---

## v0.0.9 — Bauarbeiter-Swarming-Schutz + O(1) ServiceCache

- `ConstructionHoardController` + `ConstructionHoardPlan`: Ressourcen-Reservierungs-Karte verhindert 50 Bauarbeiter auf 1 Holz.
- `ServicePlanController.serviceCache`: O(1)-Lookup statt O(n) pro Bürger pro Tick.
- Starvation-EventLog: `BrokeFoodPlan` loggt "STARVATION" vor dem Tod.
- 13 Lohnkonstanten auf 50 ausgerichtet (`resetLaborDefaults` korrespondiert).
- `battleThreat`-SEAM-Log in `EconSnapshot.stats()`.

---

## v0.0.8 — Audit-Fixes + Advisor-Meilensteine

- `EconIndicators` aus dem `roster<2`-Guard herausgelöst — Trend-Pipeline lief seit dem ersten Commit nie in echten Spielständen.
- 5 Debug-Strings (TopBar/Toggle-Texte) entfernt.
- 10 Meilensteine im Advisor-Tab renderbar (2 neue `advMsFirstTemple`/`advMsFirstEmbassy`).

---

## v0.0.7 — Subsistenz-Druck + Oddjob/Lager-Automation

- `PovertyPressure`: BValue-Booster Armut → Unzufriedenheit.
- `OddjobAutomation`: dynamische Tagelöhner-Lohn-Anpassung.
- `StateWageMarket.forceHire()`: staatliche Lohnunterbietung.
- `WarehouseAutomation`: proaktive Auto-Bewirtschaftung mit Budget-Awareness.

---

## v0.0.6 — Gini→Loyalty + Stufen-Freischaltung

- `GiniConsequences`: Gini→`BEHAVIOUR.LOYALTY` (`loyaltyAtMaxGini` Default 0.85).
- 5-Stufen-System sichtbar (EventLog "STAGE"): SUBSISTENZ→HANDEL→INDUSTRIE→WOHLSTAND→IMPERIUM.
- `propertyMarketEnabled`/`homePurchaseEnabled`/`workplaceSharesEnabled` Defaults `false`.
- Save-Migration v33: `rawLevel + 1` für Pre-v0.0.6-Saves.

---

## v0.0.5 — Property-Markt

- Hauskauf, Firmen-Anteile, Dividenden (`PropertyLedger.payDividends`).
- `WealthHappiness`-Booster ab WOHLSTAND-Stufe.

---

## v0.0.4 — Scarcity→Price→Priority-Kopplung

- `FlowPrices`: `anchorPrice × (1 + max(0, 1 − coverage)²)`.
- `ScarcitySignal` 0..1 mit Stock-Delta + Demand-Check.
- `LaborMarket.setScarcitySignal()`: knappe Ressourcen pushen die Priorität der produzierenden Firmen.

---

## v0.0.3 — roundingDrift-Fix + Housing-Miete im Audit

- `AuditKernel.Conservation` klein-residual → `roundingDrift` einsammeln.
- `HousingMarket.collectRent()` + `evict()` im Audit-Track.

---

## v0.0.2 — Chunked save/load

- `EconomySim.saveChunked(/loadChunked)`: TLV mit `TAG_*` pro Subsystem. Unbekannte Tags werden übersprungen statt Save zu zerstören.
- Kausale Wirtschaft: Arbeit löst Einkommen aus, das Löhne, Konsum und Steuern triggert.
- Zweistufige UI: Berater im Cockpit, Details im Lagerfenster.

---

## v0.0.1 — Berater-Tab, Erstveröffentlichung

- Fork von TiredGirl4's Economy Mod.
- Berater-Tab mit 10 Meilenstein-Indikatoren + KPI-Grid.
- Wirtschafts-Hauptschleife: `Roster.rebuild() → Wallets.exchange() → Fiscal.disburse() → AuditKernel`.
