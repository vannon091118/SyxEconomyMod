# SyxEconomyMod — Changelog

> Vollständige Historie. Die `pom.xml` enthält nur die letzten 10 Einträge als Release-Summary.
> Versionierung: 0.0.1+-Schritte (Pre-Release), kein 1.x bis zum ersten Public Release.

---

## v0.1.3 — 2026-07-23

### Cheat-Loop gestoppt + Save/Load-Datenverlust-Audit eingeleitet

**`EconConfig.foodAffordabilityGateEnabled`: Default `false` → `true`**
- Schließt den stillen Geld-Drucker: Mit `gate=false` UND `handoutWalletAmount=400` produzierte die Simulation reine Geldschöpfung. 200 Bürger × 400 D Handout pro Saison = **80.000 D/Saison** ohne Sink, ohne Gegenleistung. Eine direkte Folge war der Gini-Drift Richtung 1.0, der mit jedem Save schlimmer wurde und in Test-Spielständen bei `Treasury = -900M` und `Gini = 0.95` endete.
- Mit `gate=true`: Food kostet wieder Geld, Hunger = echter Druck, Handout = tatsächliche Notfallreserve statt Cashflow-Trick.
- UI-Slider im Advisor-Tab bleibt erhalten — Spieler können den Gate bewusst ausschalten (z. B. für Test-Saves oder Sandbox-Szenarien).

**Identifiziert (Phase-4.7-Blocker): `IdentityHashMap`-Datenverlust nach Save/Load**
- 9 Dateien in `src/vannon/syx/economy/core/` nutzen `IdentityHashMap<Object, X>` als Key. Songs of Syx instanziiert `RoomInstance`, `StockpileInstance`, `Induvidual`, `Humanoid`, `RoomBlueprintImp` nach Load neu → andere Object-Identity → Map liefert ohne Fehler `null`, Wirtschaftsdaten verschwinden still.
- Migration auf stabile Long-IDs (Wallets-Pattern mit `id() & 0xFFFF` + Slot-Owner-Validierung) ist Phase-4.7-Blocker #8 — vor jedem Phase-5-Feature zu addressieren.

---

## v0.1.2 — 2026-07-23

### Hotfix: Phantom-Profit, globales Rate-Limiting, insolvenzsichere Gewinnbeteiligung

**`AccessAutomation.java`: Globaler Rate-Limiter**
- `lastErrorLogTick` und `accessDetectionDisabled` sind jetzt `static`.
- Das ursprüngliche Fix (instanz-lokales Rate-Limit) warf bei 15–20 parallel laufenden Scannern immer noch einen Log-Eintrag pro Instanz. Jetzt teilen sich alle Scanner ein gemeinsames Limit: maximal einmal alle 100 Ticks ein Reminder, unabhängig von der Anzahl aktiver Raum-Scans.

**`FlowMeter.java`: Physische Produktionsrate**
- `FirmState.sample()` berechnet `outputRate` und `inputRate` jetzt aus den tatsächlichen physischen Deltas (`producedDelta / elapsedDays`, `consumedDelta / elapsedDays`) statt aus der Tageskapazität (`resource.day.getD`).
- Behebt den „Phantom-Profit“-Bug, bei dem z. B. der Zimmermann Profit zeigte, obwohl `out0_producedDelta = 0` war. Profit und CSV-Export reflektieren jetzt echte Produktion.

**`EconConfig.guildSurplusMinProfitPerWorker` neu = 10.0**
- Neuer Sockel pro Arbeiter für die Gewinnbeteiligung. Eine Firma muss erst diesen Profit pro Arbeiter erwirtschaften, bevor `guildSurplusShare` auf den Restgewinn angewendet wird.
- Verhindert, dass Subsistenz-Betriebe (Bäckerei mit 187 D/Tag, Holzfäller mit <1 D/Tag) durch die 25 %-Auszahlung insolvent werden.

**`FirmLedger.java`: Tiered Surplus Distribution**
- Statt `state.profit * share` wird nur noch `max(0, state.profit - workerCount * guildSurplusMinProfitPerWorker) * share` in `incomeCarry` eingezahlt.
- Profitable Betriebe verteilen weiterhin Geld an Arbeiter; Marginal-Betriebe bleiben liquide.

**`EconomyWindow.java`: Debug-Tab erweitert**
- Balance-Levers-Sektion zeigt jetzt auch `guildSurplusMinProfitPerWorker` an.

---

## v0.1.1 — 2026-07-23

### Balance-Krisen-Fixes (live aus Save 35505704218901)

- **`EconConfig.minimumWorkersPerWorkplace`: 0 → 1**
  - Firma-Cold-Start-Death-Spiral behoben. Neue/Zwerg-Firmen (z. B. Zimmermann) starten mit mindestens einem Arbeiter, statt auf `neededSet(0)` zu verharren. Vanilla weist dann Worker zu; der Sizing-Hillclimber optimiert danach weiter.
- **`EconConfig.guildSurplusShare`: 0.0 → 0.25**
  - Bisher blieb der gesamte Firmenüberschuss in der Firma hängen (`incomeCarry` wuchs nie). Ein Viertel des Surpluses fließt jetzt an die Beschäftigten zurück. Dämpft Gini-Anstieg und Null-Geld-Masse der Bevölkerung, ohne inflationär zu überkorrigieren.
- **`EconConfig.perHeadTaxExemptionThreshold` neu = 500**
  - Kopfsteuer trifft jetzt nur noch Bürger ab einer Vermögensfreigrenze. Null-/Fast-Null-Bürger rutschen nicht mehr in staatliche Schuldknechtschaft, nur weil der Staat sie täglich besteuert.
- **`Fiscal.java`: Per-Head-Steuer mit Armutsfreigrenze**
  - Bürger mit `netWorth < perHeadTaxExemptionThreshold` werden in der Steuerschleife übersprungen, bevor eine Belastung oder Schuld eingetragen wird.

### Stabilität

- **`AccessAutomation.java`: Rate-Limited Reminder-Log**
  - Der "Zugangs-Erkennung deaktiviert"-Reminder spammt nicht mehr pro Tick. Statt `ticks - last >= 100` (brach bei Tick-Reset/Wrap-Around) wird jetzt `ticks >= last + 100 || ticks < last` verwendet. Maximal ein Log alle 100 Ticks oder einmal nach Save/Load-Reset.

---

## v0.1.0 (Phase 4) — 2026-07-23

**Adapter-Architektur:** 5 Vanilla/Fallback-Interfaces kapseln sämtliche Reflection-Zugriffe:
- `ISyxAI` — 6 Plan-Klassen-Erkennung via `Class.forName` (kein `getSimpleName()`-String-Vergleich mehr)
- `ISyxTransport` — `TransportInstance.distance` (float-Feld, `Field.getFloat` + Cast)
- `ISyxWarehouse` — `StockpileInstance.storingSet(boolean)`, umbenannt zu `hasStoringLock()`
- `ISyxBoosting` — `BOOSTABLES.CIVICS().GOV` Boostable für Admin-Bonus
- `ISyxDiplomacy` — 5 `DipWarPlayer`-Felder (`upI`, `pPow`, `coalitionPow`, `bWilling`, `willing`)

**TreasuryCrisis (5 Tier-Stufen + Hard-Floor-Verhalten in Tier 5):** Eskalierende Krisenmechanik mit Hysterese. Tier 1 Warnung (≤−5K) → Tier 2 Sparprogramm (≤−50K, 15 Lohnkonstanten halbiert) → Tier 3 Zwangs-Liquidation (≤−250K) → Tier 4 Staatsbankrott (≤−1M, 4 Systeme deaktiviert) → Tier 5 Kollaps + Hard Floor (≤−5M, alle 11 Systeme deaktiviert, LOYALTY −50%). Vorher: 0 Treffer für `treasuryFloor`/`debtCeiling` im gesamten Codebase — Staatskasse konnte auf −900M fallen ohne Konsequenz.

**DiagnosticExporter:** Opt-in CSV-Export (3 Dateien pro In-Game-Tag):
- `rebalance_macro_*.csv` — Makro-Kennzahlen (**32 Spalten**)
- `rebalance_resources_*.csv` — Resource-Metriken (10 Spalten, Long-Format)
- `rebalance_firms_*.csv` — Firmen-Profitabilität (13 Spalten, inkl. FlowMeter-Daten)

**Rebalancing-Dashboard:** Python/Jupyter mit 5 Plots:
- Resource-Scarcity-Heatmap
- Macro-Trend-Stacked (6 Subplots)
- Anchor-vs-Market-Price-Drift
- Gini-vs-Treasury (Dual-Axis)
- Firm-Profitability (2×2 Grid)

**Weitere Änderungen:**
- `FirmLedger`: per-Firm-Income-Tracking, `FirmFinancialSnapshot`-Record
- `EconProgression`: `ISyxBoosting`-Constructor-Injection, Reflection-Loop entfernt
- `DebtDiplomacyBuffer`: `ISyxDiplomacy`-Constructor-Injection, 5 `Field`-Felder entfernt
- Alle 14 Adapter-Signaturen gegen `SongsOfSyx-sources.jar` verifiziert (1 Bug gefunden + gefixt: `distance` ist `float`, nicht `double`)
- `pom.xml`-Metadaten-Sync: Version auf 0.1.0, Changelog auf 10 Einträge gekappt, `mod.version.history` statisch, `CHANGELOG.md` als Vollhistorie
- `tools/verify-version-consistency.sh` — Pre-Commit-Hook für Version↔Changelog-Konsistenz

---

## v0.0.9

- Bauarbeiter swarming-Schutz
- Starvation EventLog
- O(1) ServiceCache-Lookup im ServicePlanController
- Lohnkonstanten-Fix

## v0.0.8

- Audit-Fixes: EconIndicators, Debug-Flags, tote Strings
- Advisor Meilensteine

## v0.0.7

- PovertyPressure
- OddjobAutomation
- WarehouseAutomation
- StateWageMarket-ForceHire

## v0.0.6

- Gini → Loyalty
- Wirtschaftsstufen-Sichtbarkeit/Freischaltung
- Trend-Konsequenzen

## v0.0.5

- Property-Markt: Hauskauf, Firmen-Anteile, Dividenden

## v0.0.4

- Scarcity → Price → Priority-Kopplung

## v0.0.3

- roundingDrift-Fix
- Audit-Komplettierung
- Housing-Miete im Audit getrackt

## v0.0.2

- Chunked save/load
- Zweistufige UI
- Kausale Wirtschaft

## v0.0.1

- Berater-Tab
- Erstveröffentlichung (Fork von TiredGirl4 Economy Mod)
