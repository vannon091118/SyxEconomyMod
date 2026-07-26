# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.51 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: `tools/verify-doc-sync.sh` (9 Checks).
> Abgeschlossene Sprints → [`CHANGELOG.md`](CHANGELOG.md).

---

## Global Task Index (Backlog)

**Legende:** 🔴 P0 · 🟠 P1 · 🟡 P2 · 🟢 P3

| Task | Prio | Kurzbeschreibung | LoC | Sprint |
|---|---|---|---|---|
| **7-1a** | 🟠 P1 | Balance-CI: Scarcity-Kaskaden-Algorithmus dokumentieren (Spezifikation aus `FlowPrices.java:scarcityMultiplier()` + `LocalPrices.java:scarcity()` + `EconConfig.scarcityElasticityUp/Down`; Konstanten UP=0.8 / DOWN=1.375 / Clamp=100. **Reihenfolge:** 7-1a vor 7-1b, 7-1b vor 7-2. | ~35 | 9 |
| **7-1b** | 🟠 P1 | Balance-CI: Golden-Snapshot-Erzeugung gegen `EconConfig`-Formeln (Ist-Output für 7-2-Toleranzcheck). **Reihenfolge:** 7-1a (Algorithmus) vor 7-1b. **Hinweis:** NICHT durch Wiederbelebung der gelöschten `tools/scarcity_sim.py` — Build aus den vorhandenen Engine-Klassen. | ~25 | 9 |
| **7-2** | 🟠 P1 | `tools/balance-smoke.sh`: CSV→Toleranz-Check→Build-Break (5%). Hängt von 7-1 ab. | ~40 | 7 |
| **7-3** | 🟡 P2 | Booster-Eval: 6 Behaviour-Booster auf Mod-Relevanz messen. **Anti-Bias-Wording (Rule 1.6):** Ergebnis `0/6` ist valider Ausgang. Booster werden NICHT um einer Quote willen integriert ("3/402 → 6/402" war Kennzahl-Optimierung, nicht Spielentscheidung). Wenn keiner der 6 für die Mod-Mechanik relevant ist → Sprint-Abschluss ohne Booster-Integration. `ggf.`-Pfad als selbständiger Folgesprint (z.B. B-013), niemals in Sprint-9-commit mit-mischen. **Reihenfolge:** Sprint 9 ticket `8-1` (Mockito-Coverage) liefert die Mess-Basis. | ~30 | 9 |
| **8-1** | 🟠 P1 | Mockito-Inject Coverage: Fiscal/Housing/Labor/Affordability/EconProgression + JaCoCo line=30% / branch=15%. **Anti-Bias-Wording (Rule 1.6):** Schwellen sind Ziel-Werte, keine Pflicht-Quoten. Wenn Coverage nach Mockito-Inject unter Schwellen bleibt → Sprint-Abschluss mit dokumentiertem Befund, kein Pflicht-Sprint-Folgesprint. `ggf.`-Pfad als B-014 (separater Sprint). **Reihenfolge:** Unabhängig vom 7-1a/b/2-Pfad; Mess-Basis für 7-3. | ~600 | 9 |
| **8-2** | 🟡 P2 | 5 ungetestete Klassen: NpcFactionAdapter, AdapterDispatcher, SchemaValidator, DebugCsv, LoggingAdapter | ~200 | 8 |
| **8-3** | 🟡 P2 | FlowMeter: `SETT.ROOMS().ins()` für ROOM_PRODUCER_INSTANCE iterieren (B-001) | ~25 | 8 |
| **8-4** | 🟡 P2 | Oddjob-Clamp: harte Grenze via `EconConfig.oddjobMaxPay` (B-005) | ~12 | 8 |
| **8-5** | 🟡 P2 | Hungersignal → Bevölkerungs-Kopplung: MeticImmigration + Roster (B-009) | ~18 | 8 |
| *B-004* | 🟢 P3 | Vermögensklassen-Drift: WealthStats ↔ CitizenClass angleichen | ~30 | — |
| *B-002* | 🟢 P3 | AccessAutomation-Spam: Rate-Limiter für Statusmeldungen | ~6 | — |
| *B-006* | 🟢 P3 | IdentityHashMap-Migration Phase 2/3 | ~50 | — |
| *B-008* | 🟢 P3 | EngineSeams-Direkt-Calls: 31→0 | ~40 | — |
| *B-010* | 🟢 P3 | Carpenter targetWage=0 in FlowPrices | ~8 | — |
| *T22* | 🟢 P3 | Savegame-Compat-Headless-Test | ~50 | — |
| **D-001** | 🔴 P0 | **Food-Price-Hyperinflation fixen.** Diagnostik 3 Spielstände zeigen: `food_basket_price` explodiert auf 70–85× Anker bei Knappheit. `FlowPrices.scarcityMultiplier()` hat kein sinnvolles `priceAbsoluteMax` für Nahrungsmittel. Kaskade: Preise×80 → Bürger verarmen → Treasury kollabiert → Emigration → Teufelskreis. **Quelle:** `rebalance_resources_*.csv` (BREAD 78→6248, FISH 200→16974), `rebalance_macro_10927618689179.csv` (Tag 227: treasury=−6988). **Prüfen, weil:** Ohne Preisdeckel ist jeder Spielstand >150 Tage deterministisch in TreasuryCrisis. | ~20 | 10 |
| **D-002** | 🔴 P0 | **Emigration-Kaskade bei median_wealth=0.** Diagnostik: Ab Tag 260 (Seed 10927618689179) ist `median_wealth=0`, Emigration 9/Tag, Bevölkerung fällt 95→86. Kopplung an `MeticImmigration` + Hungersignal (B-009) ist zu aggressiv. **Prüfen, weil:** Emigration verstärkt den Teufelskreis (weniger Produktion → mehr Knappheit → mehr Emigration). Dependency: D-001 muss zuerst (Emigration ist Symptom der Inflation). | ~18 | 10 |
| **D-003** | 🟠 P1 | **Carpenter Cold-Start nach Save/Load.** `furniture_debug.csv`: 200+ Ticks 0 Output trotz `hardTarget=2`, `physicalSeen=true`. `FirmLedger.HillState` wird nicht persistiert → nach Load: `hill=null` → `shouldIdle()` bei `profit=0` → Target auf Minimum → Catch-22 (kein Output→kein Profit→kein Output). **Prüfen, weil:** Jede Firma die nach Save/Load 0 Profit hat, wird permanent stuck. Erweitert B-010 (targetWage=0). | ~30 | 10 |
| **D-004** | 🟠 P1 | **_WOOD-Preis-Inversion: Broken-Link-Analyse.** Diagnostik: `_WOOD` Anker=148, Markt=8 (0.05×), Coverage=8.723 bei stock=414 und demand=41.93. `FlowPrices.effectiveCoverage()` zählt physischen Lagerbestand als ob es Supply wäre, obwohl kein Zufluss kommt. **Prüfen, weil:** Carpenter bekommt kein Holz obwohl Lager voll — disconnect zwischen Lager-Tracking und Firmen-Input. | ~25 | 10 |
| **D-005** | 🟡 P2 | **Wealth-Concentration-Clamp (Gini 0.95+).** Diagnostik: Gini 0.62→0.95 in ~60 Tagen. `incomeCarry` akkumuliert bei profitablen Firmen ohne Deckelung. `guildSurplusShare` verteilt Gewinne nur an Besitzer. **Prüfen, weil:** Extreme Ungleichheit destabilisiert das gesamte Wirtschaftssystem (kein Konsum → keine Firmenrevenue → mehr Ungleichheit). | ~15 | 10 |
| **D-006** | 🟠 P1 | **UI-Struktur-Verifikation nach Sprint-9-Änderungen.** Commits `04a125e`, `e33187c`, `e261ca5`, `b0c8ac1`: (1) `DebugTab` jetzt permanent in `TABS[]` statt conditional `debugLoggingEnabled` — ARCHITECTURE.md §6-Tabs-Zählung prüfen (6→7?). (2) Window-Panels 840×620 statt bisheriger Größe. (3) HUD-Ampel-Bars + Onboarding-Tutorial in Tab 1 + direkte Player-Agency-Controls. (4) Import-Refactoring inline→explicit. **Prüfen, weil:** Rule 6 (UI-Struktur ist heilig) — ARCHITECTURE.md behauptet "6 Tabs in WindowState"; DebugTab war hidden, jetzt sichtbar. Stam-Docs müssen synchronisiert werden. | ~15 | 10 |

**Total:** 21 Tasks — 2×P0 Diagnostik (D-001, D-002), 4×P1 Sprint 9 + 3×P1 Diagnostik (D-003, D-004, D-006), 5×P2 Sprint 9 + 1×P2 Diagnostik (D-005), 6×P3 (Backlog).

**Sprint 9 Dependency-Edges (Rule 1.7 Pre-Note):**

```
  7-1a (Algorithmus-Doku, ~35 LoC)  -->  7-1b (Snapshot-Erzeugung, ~25 LoC)  -->  7-2 (balance-smoke Gate)
  8-1 (Mockito-Coverage) ist unabhaengig vom 7-1a/b/2-Pfad
  7-3 (Booster-Eval) nutzt 8-1 als Mess-Basis, sonst unabhaengig
  8-2..8-5 (Probe-Objekte + B-001/FlowMeter + B-005/Oddjob + B-009/Hunger) sind unabhaengig
  D-001 (Food-Price-Hyperinflation) --> D-002 (Emigration-Kaskade) — Symptom-Reihenfolge
  D-001 (PriceCap) --> D-003 (Carpenter Cold-Start) — Preisdeckel fixt Profit=0 erst
  D-004 (_WOOD-Broken-Link) ist unabhaengig von D-001..D-003
  D-005 (Wealth-Concentration) ist unabhaengig, aber Ergebnis von D-001 cascade
  D-006 (UI-Struktur-Verifikation) ist unabhaengig von allen D-Tasks
```

Maschinenlesbare Validation der Dep-Edges ist Sprint-10-Folgeaufgabe (Gate 11 in eigenem Folge-Sprint).

---

## Definition of Done

1. `mvn verify install -DskipTests` — 7/7 Gates
2. `mvn test` — 296 Tests, 0 Fail
3. `bash tools/verify-doc-sync.sh` — 9 Checks PASS
4. Pre-Commit-Hook: `.git/hooks/pre-commit → tools/build-gate.sh`

---

## Freeze-Status (seit Sprint 6)

| Schicht | Status |
|---|---|
| `core/`, `ui/`, `adapter/` | ❄️ **FROZEN** |
| `tools/vanilla-schema.yaml` | ✅ Engine-Updates (V72: 1 Diff) |
| `EconConfig.java` | ✅ Balancing-Parameter |
| `test/` | ✅ Neue Tests |
