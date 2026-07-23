# 04 — Refactoring & Development Roadmap

> **Version:** v0.1.0 | **Stand:** 2026-07-23 | **Spiel:** V71.44
>
> **Stand der Roadmap vs. Code (Session-Audit 2026-07-23):** Phase 0–4 alle abgeschlossen. Phase 5 (Tests) noch offen. Advisor-Visualisierungs-Konzept bleibt aspirational.

---

## ✅ Abgeschlossen

### Phase 0: Stabilisierung (2026-07)
- Package-Rename, Decompiler-Artefakte bereinigt
- Maven-Build stabilisiert (`mvn clean install`)
- 3 Hauptmenüs + 15 Tabs + Debug-Tab im Wirtschaftsfenster
- Causal-Economy-Redesign: Bau-Nachfrage, Gewinn-Priorität, Job-Friction
- Chunked Save/Load (Format 33, INDUSTRIE-Migration)

### Phase 1: Miete & Unterhalt ✅
- Pro-Haushalt-Mietabrechnung (`HousingMarket`)
- Miet-Schulden von Steuer-Schulden getrennt
- Zwangsräumung nach Grace-Days
- Miete im Audit getrackt

### Phase 2: Eigentum & Privatisierung ✅
- Grundstücksregister (`PropertyLedger`) — Hauskauf, Firmenanteile, Dividenden
- Freischaltung ab WOHLSTAND/IMPERIUM
- Firmenbesitz-Panel im BETRIEBE-Tab

### Phase 3: Makro-Economy UI ✅
- EventLog mit 100er Ringpuffer, 8+ Kategorien
- Gini→Loyalty Coupling (`GiniConsequences`)
- Trend-Erkennung mit echten Konsequenzen
- Berater-Dashboard mit KPI-Boxen, Sparklines, Warnketten
- SEAM-EventLog-Hooks: alle stummen Catches instrumentiert
- PovertyPressure, OddjobAutomation, WarehouseAutomation

### Phase 4: Adapter-Layer ✅ (6/6 abgeschlossen)

| Schritt | Interface | Status |
|---------|-----------|--------|
| 5.1 | `ISyxAI` + `VanillaAIAdapter` | ✅ |
| 5.2 | `ISyxTransport` + Vanilla/Fallback/MH | ✅ |
| 5.3 | `ISyxWarehouse` + Vanilla/Fallback/MH | ✅ |
| 5.4 | `ISyxBoosting` + Vanilla/Fallback | ✅ |
| 5.5 | `ISyxDiplomacy` + Vanilla/Fallback/MH | ✅ |
| 5.6 | Cleanup (@Deprecated löschen, Aufrufer migrieren) | ✅ |

**17 Adapter-Dateien** in `src/vannon/syx/economy/adapter/`. 0 direkte Reflection-Stellen im Core-Code. Details: [PHASE4_ADAPTER_PLAN.md](PHASE4_ADAPTER_PLAN.md).

### Phase 4a: Crash-Härtung ✅
- **Class.forName-ClassLoader-Fix**: `Class.forName(name)` → `Class.forName(name, true, GAME_CL)`. Package-private Spiel-Klassen jetzt sichtbar.
- **openCsvFolder-Crash**: `Files.createDirectories()` vor `Desktop.open()`, Catch-Blöcke auf `RuntimeException` erweitert.
- **catch-Tightening**: 10 Sites von `catch(Throwable)`/`catch(Exception)` auf `catch(RuntimeException)`.
- **Build-Gate-System**: 3 Pre-Compile-Gates (Code Audit, Version↔Changelog, Adapter↔Engine). 0 Failures, 0 Skips.

### Phase 4b: Treasury-Krisenmechanik ✅
- **5 Tier-Stufen + Hard-Floor** (`TreasuryCrisis.java`):
  - Tier 1 (< −5K): EventLog-Warnung + Stopp Subventionen/Transport/Handouts/Auto-Tunes
  - Tier 2 (< −50K): ALLE 15 Lohnkonstanten halbiert, Marktsteuer +15%, Dole-Headcap 50%
  - Tier 3 (< −250K): Zwangsliquidation aller Staatslager + erzwungener Immobilienmarkt
  - Tier 4 (< −1M): Kopfsteuer=500, Marktsteuer=50%, Corvée/Dole/Schuldknechtschaft/Wages deaktiviert
  - Tier 5 (< −5M): ALLE 11 Systeme deaktiviert, LOYALTY −50% (**Hard Floor**)
- **Hysterese**: Erholungsschwellen höher als Aktivierung (0/−10K/−50K/−250K/−1M).
- Vorher: 0 Treffer für `treasuryFloor`/`debtCeiling`/`bankrupt` im gesamten Codebase.

### Phase 4c: Balancing-Fixes ✅
- **Stage-gated Wallet**: Startguthaben skaliert mit Wirtschaftsstufe (200→500→2000→5000). Keine 1M Scheinwirtschaft am Tag 0.
- **Arbeitende-ärmer-als-Chiller-Bug**: `freeRation()` Safety-Net entfernt, HandoutRelief nur für Beschäftigte, Lohn-Untergrenze ≥ Handout.
- **WarehouseAutomation proaktiv**: Auto-Erkennung knapper Ressourcen, Nahrungs-Notfallpuffer, Budget-Check.

### Phase 4d: Diagnostik ✅
- **DiagnosticExporter**: 3 CSVs/Tag (Macro 32 Spalten, Resources, Firms).
- **Hard-Threshold-Alert-Layer**: [REBALANCE]-EventLog bei Gini>0.40 oder Audit-Delta>1000.
- **Rebalance-Dashboard**: 5 Plots (Python/Jupyter, pandas+matplotlib).
- **Debug-Tab**: Live-Snapshot + "CSV-Pfad öffnen"-Button.

---

## 🚧 Offen

### Phase 5: Tests & Modularisierung (**1/3 begonnen**)
- ✓ JUnit für `TreasuryCrisis.classify()` + Recovery-Logik — `test/.../TreasuryCrisisTest.java`
- 🔧 JUnit für `ExchangeKernel` (Wealth-Konservierung)
- 🔧 JUnit für `FlowPrices` Elastizität
- 🔧 `EconomySim` in Module zerlegen

### Phase 5a: Per-Citizen Training-EXP + Needs-Bridge (0/8 Klassen implementiert)
- Vollständiger Plan verifiziert: [`docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md`](superpowers/plans/2026-07-23-per-citizen-training-exp.md) (11 Tasks, 8 Klassen, 3 Tests)
- Vanilla-API gegen `info.zip/SongsOfSyx-sources.jar` source-verifiziert (NEEDS-, BValue-, BOOSTABLES-, StatsWork-Signaturen)
- Geplante Klassen: `StageGate`, `NeedsBridge`, `ITrainingSource`, `InternalTrainingSource`, `CsvTrainingSource`, `CsvIngest`, `TrainingVectors`, `CitizenLayers`
- **0/8 implementiert.** Aufwand-Schätzung: 2–3 Sessions.

### Phase 5b: UI-Politur (0 Engine-Widgets genutzt)

**Analyse (2026-07-23):** 84 Screenshots dokumentieren 16 funktionale Tabs — aber alle Widgets sind handgeschrieben. Die Engine stellt `GMeter`, `GChart`, `GButt.Checkbox`, `GSliderInt`, `hoverInfoSet()`, `GCOLOR.UI()` bereit — nichts davon wird genutzt.

| Gap | Aufwand | Nutzen |
|-----|---------|--------|
| `GMeter` für Coverage/Scarcity-Fortschritt | Klein | Status-Visualisierung |
| `GChart` für Treasury/Gini-Trends | Mittel | Sparklines ohne Eigenbau |
| `GButt.Checkbox` für alle Toggles | Klein | Native UI-Konsistenz |
| `hoverInfoSet()` für Tooltips | Mittel | Erklärungen im Hover |

### Phase 5c: GUI-Gap schließen (P1-P3 aus Gap-Analyse)

| Gap | Zugänglichkeit | Aufwand | Priorität |
|-----|---------------|---------|-----------|
| `STATS.BATTLE()` einlesen | ✅ Direkt | Klein | 🟡 P1 |
| `BOOSTABLES.PHYSICS().HEALTH` nutzen | ✅ Direkt | Klein | 🟡 P1 |
| `FCredits.CTYPE`-Einnahmen aufschlüsseln | ✅ Direkt | Klein | 🟢 P2 |
| RoomStats-Bridge (Tätigkeit 1.5%) | ❌ Package-Private | Groß | 🔴 P3 |

### Phase 6: Advisor-Visualisierungs-Konzept (aspirational)

Das Advisor-Visualisierungs-Konzept (unten) beschreibt 5 Panels mit Status-Gauges, Preis-Sparklines, Kausal-Warnketten und Makro-Trends. Alle Datenquellen existieren im Code — nur das Rendering fehlt. Nächster logischer Schritt nach Phase 5.

---

## Technische Kennzahlen (aktuell)

| Metrik | Wert |
|--------|------|
| Java-Dateien | **112** |
| Adapter-Dateien | 17 |
| Reflection-Stellen (direkt) | 0 (alle via Adapter) |
| Build-Gates | 3 (0F/0S) |
| catch(Throwable) außerhalb adapter/ | 0 |
| Treasury-Tiers | 5 (+ Hard Floor in Tier 5) |
| CSV-Exporte | 3 pro Spieltag (Macro: 32 Spalten, Resources: 10, Firms: 13) |

---

## Definition of Done

- All new code compiles against vanilla `SongsOfSyx.jar`
- Unit tests pass (sofern vorhanden)
- `mvn compile` → BUILD SUCCESS, 3 Gates bestanden
- Keine stummen Catch-Blöcke
- Keine direkten Reflection-Zugriffe außerhalb adapter/

---

## Anhang: Advisor-Visualisierungs-Konzept (aspirational)

> ⚠️ **KONZEPT — nicht implementiert.** Dies ist ein aspirationales Design-Dokument.
> Stand: Juli 2026. Datenquellen alle im Code vorhanden, Rendering fehlt.

### Designprinzipien

1. **Eine Information, eine Zeile.** Jede Zeile beschreibt einen Zustand, nicht nur eine Zahl.
2. **Trend vor Zahl.** Sparklines und Pfeile zeigen Richtung; absolute Werte in Klammern.
3. **Farbe als Semantik.** Rot = Handlungsbedarf, Gelb = beobachten, Weiß/Grau = stabil.
4. **Kausal statt symptomal.** Warnungen sagen *warum* etwas passiert und *wohin* es führt.

### Datenquellen

| Quelle | Relevante Felder | Bedeutung |
|--------|-----------------|-----------|
| `EconSnapshot` (Ring-Buffer) | `foodBasketPrice`, `meanWage`, `gini`, `wageShare`, `totalMoney`, `workersUnpaid`, `emigrations` | Historische Makro-Trends |
| `EconIndicators` | `isInequalityRising()`, `isWagesFalling()`, `isTreasuryDeclining()` | Trend-Flags |
| `FlowPrices` / `FlowMeter.Snapshot` | `price`, `anchor`, `coverage`, `supply`, `demand`, `stock` | Per-Güter-Preise |
| `WealthStats` | `gini`, `median`, `mean`, `histogram` | Vermögensverteilung |
| `FirmLedger` | `lastWorkersUnpaid()`, `lastIncomeDue()`, `lastIncomePaid()` | Betriebsgesundheit |

### 5 Panels

```
[1] Wirtschafts-Ökosystem (Status-Gauges: Geldmenge, Produktion, Nahrung, Wohlfahrt, Staatskasse)
[2] Kritische Preis-Trends (Sparklines pro Gütergruppe: Nahrung, Bau, Handel, Getränke)
[3] Kausale Warnketten (Engpass→Preise→Löhne→Abwanderung, etc.)
[4] Makro-Trends (2×2: Nahrungskorb, Ø Lohn, Gini, Lohn-Anteil)
[5] Steuerung / Empfohlene Schieber (Kopfsteuer, Markt-Abschöpfung, Gelegenheitslohn)
```
