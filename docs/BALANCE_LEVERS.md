# Balance-Levers

> **Kurzreferenz für alle ökonomischen Hebel, die einen Save schnell retten oder zerstören können.**
>
> Version: v0.1.1 | Spiel: Songs of Syx V71.44

Diese Datei listet die wichtigsten konfigurierbaren Konstanten in `EconConfig.java` zusammen mit ihrer Wirkung, ihrer Gefahr und Empfehlungen für das Tuning. Sie ersetzt nicht die Javadoc in `EconConfig`, sondern fasst die Hebel aus Sicht eines Balancers/Designers zusammen.

---

## 1. Firma-Cold-Start

| Feld | Wert | Datei |
|------|------|-------|
| `minimumWorkersPerWorkplace` | `1` | `EconConfig.java` |

**Was es tut**
- Legt die Mindestanzahl an Arbeitern fest, die eine Firma beim Start/Zurücksetzen behauptet.
- Vorher `0`: Firmen wie der Zimmermann blieben bei `neededSet(0)`. Vanilla weist keine Arbeiter zu, die Firma produziert nie und verharrt in `shouldIdle()`.
- Ab v0.1.1 `1`: Jede Firma startet mit mindestens einem Worker, produziert also und kann wachsen.

**Wirkung im Save**
- Bäckerei, Weber, Steinmetz, Schmied usw. kommen aus dem Tiefschlaf.
- Risiko: Bei sehr kleinen Märkten kann eine einzelne überflüssige Firma leicht Verlust machen. Das ist jedoch bewusst in Kauf genommen, weil der Status „produziert nie“ wirtschaftlich falscher ist.

**Tuning-Empfehlung**
- Für den Großteil der Spielstände `1` belassen.
- Nur wenn der Spieler absichtlich Mikro-Firmen ohne Produktion haben will, auf `0` zurücksetzen (nicht empfohlen).

---

## 2. Gewinnverteilung Arbeitnehmer

| Feld | Wert | Datei |
|------|------|-------|
| `guildSurplusShare` | `0.25` | `EconConfig.java` |
| `guildSurplusMinProfitPerWorker` | `10.0` | `EconConfig.java` |

**Was es tut**
- Anteil des Firmanprofits, der täglich an die Arbeitnehmer ausgeschüttet wird — aber nur auf den **Teil des Profits, der über `guildSurplusMinProfitPerWorker × Arbeiter` liegt**.
- `guildSurplusMinProfitPerWorker` ist ein Sockel pro Arbeiter. Ein Betrieb mit 2 Arbeitern muss also mindestens `20 D/Tag` Profit machen, bevor überhaupt etwas an die Arbeiter weitergegeben wird.
- Vorher flachte `guildSurplusShare` das Gesamtkapital ab: Bäckerei mit 187 D/Tag und Holzfäller mit <1 D/Tag wurden trotzdem mit 25 % besteuert und gingen insolvent.

**Wirkung im Save**
- Subsistenz-Betriebe bleiben liquide.
- Profitable Betriebe (Zimmermann, Steinmetz) verteilen weiterhin Geld an Arbeiter.
- Gini sinkt langsam, ohne dass Marginal-Betriebe zusammenbrechen.

**Gefahren**
- `guildSurplusShare > 0.5`: Zu viel Geld auf einmal → Inflation.
- `guildSurplusMinProfitPerWorker` zu hoch: Keine Betriebe zahlen mehr Lohnzuschlag → Gini steigt wieder.
- `guildSurplusMinProfitPerWorker` zu niedrig: Rückfall in die alte Insolvenzspirale.

**Tuning-Empfehlung**
- Start: `guildSurplusShare = 0.25`, `guildSurplusMinProfitPerWorker = 10.0`.
- Wenn weiterhin >10 Insolvenzen gemeldet werden: `guildSurplusMinProfitPerWorker` auf `15`–`20` erhöhen.
- Wenn keine Surplus-Auszahlungen mehr erfolgen: auf `5` senken.

---

## 3. Kopfsteuer-Armutsfreigrenze

| Feld | Wert | Datei |
|------|------|-------|
| `perHeadTaxExemptionThreshold` | `500` | `EconConfig.java` |

**Was es tut**
- Bürger mit `netWorth < threshold` zahlen keine Kopfsteuer.
- Entkoppelt von `doleWealthThreshold` (Kornspende), damit Steuer- und Sozialpolitik unabhängig bleiben.

**Wirkung im Save**
- Null-Bürger rutschen nicht mehr in Schuldsknechtschaft, nur weil der Staat sie besteuert.
- Treasury verliert Einnahmen bei sehr armen Populationen, verhindert aber den Gini-Druck, der aus der Kopfsteuer entsteht.

**Gefahren**
- `= 0`: Arme werden belastet → Rückfall in den alten Todesspiralenffekt.
- `> 2000`: Zu viele Bürger zahlen keine Kopfsteuer mehr → Treasury-Drain, wenn keine anderen Einnahmen da sind.

**Tuning-Empfehlung**
- `500` ist ein guter Standard (ca. 1–2 Tageslöhne).
- Wenn die mittlere Vermögenskurve stabil ist und Treasury nicht leer: leicht erhöhen (`750`).
- Wenn Treasury chronisch leer und Gini niedrig: auf `250` senken.

---

## 4. Zugangs-Erkennungs-Reminder (Stabilität)

| Feld | Wert | Datei |
|------|------|-------|
| `ERROR_LOG_INTERVAL` | `100` | `AccessAutomation.java` |

**Was es tut**
- Wenn die Raum-Scan-Loop einmal fehlschlägt, wird sie deaktiviert. Dieser Wert begrenzt, wie oft der Reminder-Log neu geschrieben wird.
- Vorher brach der Delta-Check bei Tick-Reset/Wrap-Around zusammen und spammte jede Runde.
- Ab v0.1.1: Bedingung `ticks >= last + 100 || ticks < last` ist robust gegen Reset und Integer-Wrap-Around.
- **Hotfix:** `lastErrorLogTick` und `accessDetectionDisabled` sind jetzt `static`, damit 15–20 parallel laufende Scanner ein gemeinsames Limit nutzen.

**Wirkung**
- EventLog bleibt lesbar.
- Diagnose nicht mehr unmöglich, weil ein einziger Fehler das Log überflutet.

---

## 5. Physische Produktionsrate (FlowMeter)

| Feld | Wert | Datei |
|------|------|-------|
| — | interner Algorithmus | `FlowMeter.java` |

**Was es tut**
- `FlowMeter.FirmState.sample()` berechnet `outputRate` und `inputRate` jetzt aus den tatsächlichen physischen Deltas (`producedDelta`, `consumedDelta`) statt aus der Tageskapazität (`resource.day.getD`).
- Das behebt den „Phantom-Profit“-Bug: Betriebe wie der Zimmermann konnten Profit zeigen, obwohl sie 0 Einheiten produziert haben.

**Wirkung im Save**
- FirmLedger-Profit und CSV-`out0_producedDelta` stimmen jetzt besser überein.
- Preise spiegeln echte Produktion wider.

**Gefahren**
- Sehr kurze Sample-Windows können die Rate auf 0 springen lassen, wenn gerade keine Einheit fertig wurde.
- Langfristig ist die physische Rate aber die einzige verlässliche Basis für Preise und Profit.

---

## 6. Food-Affordability-Gate (Cheat-Loop-Stopp, v0.1.3)

| Feld | Wert | Datei |
|------|------|-------|
| `foodAffordabilityGateEnabled` | `true` (Default seit v0.1.3) | `EconConfig.java` |

**Was es tut**
- Steuert, ob der Food-Plan-Ausführung am Bezahl-Gate hängt: bei `false` isst der Bürger kostenlos, bei `true` muss er Geld haben.
- Default wurde in v0.1.3 von `false` → `true` umgestellt, nachdem ein aktiver Geld-Drucker identifiziert wurde: bei `gate=false` + `handoutWalletAmount=400` produzierte die Simulation reine Geldschöpfung (200 Bürger × 400 D Handout = 80.000 D/Saison ohne Sink).

**Wirkung im Save**
- Mit `gate=true`: Hunger ist echter Druck, Bürger müssen arbeiten oder ihr Vermögen aufbrauchen. Gini bleibt in vernünftigem Korridor.
- Mit `gate=false` (bewusst): nur für Sandbox-Setups / Test-Saves, in denen ökonomische Constraints entfernt werden sollen.

**Gefahren**
- `gate=false` auf realen Saves, die auch `handoutWalletAmount > 0` haben: aktiver Cheat-Loop, Gini explodiert, Treasury-Krise künstlich.

**Tuning-Empfehlung**
- Default `true` belassen.
- Nur bewusst ausschalten, wenn die Sandbox-Funktion getestet wird oder besondere Krisen-Szenarien simuliert werden.

---

## Zusammenhang der Hebel

```
minimumWorkersPerWorkplace = 1
        │
        ▼
   Firma produziert (physische Rate aus FlowMeter)
        │
        ▼
   guildSurplusShare = 0.25  (nur auf Profit über
        │                      guildSurplusMinProfitPerWorker)
        ▼
   Arbeiter verdienen Geld
        │
        ▼
   perHeadTaxExemptionThreshold = 500
        │
        ▼
   Arme zahlen keine Kopfsteuer → Median steigt, Gini sinkt
```

---

## Wann welchen Hebel drehen?

| Symptom | Primärer Hebel | Richtung |
|---------|---------------|----------|
| Firmen bleiben bei 0 Arbeitern | `minimumWorkersPerWorkplace` | `1` |
| Einzelne Firma dominiert 70 %+ des Profits | `guildSurplusShare` | `+` |
| Gini > 0.85 und Median = 0 | `guildSurplusShare` / `perHeadTaxExemptionThreshold` | `+` / `+` |
| Viele Insolvenzen / „INSOLVENT: Gilden-Anteile“ | `guildSurplusMinProfitPerWorker` | `+` |
| Treasury-Drain wegen Steuerbefreiung | `perHeadTaxExemptionThreshold` | `-` |
| Inflation nach Gewinnbeteiligung | `guildSurplusShare` | `-` |
| EventLog spammt „Zugangs-Erkennung deaktiviert“ | `ERROR_LOG_INTERVAL` + globales `static` Limit | belassen |
| Firma zeigt Profit, produziert aber 0 | FlowMeter physische Rate | — |

---

## Siehe auch

- `src/vannon/syx/economy/core/EconConfig.java` — alle Konstanten mit Javadoc
- `src/vannon/syx/economy/core/FirmLedger.java` — Surplus-Auszahlungslogik
- `src/vannon/syx/economy/core/FlowMeter.java` — physische Input/Output-Raten
- `src/vannon/syx/economy/core/Fiscal.java` — Steuerlogik inkl. Freigrenze
- `src/vannon/syx/economy/core/AccessAutomation.java` — globale Rate-Limiting
- `CHANGELOG.md` — vollständige Versionshistorie

---

## Truth-Status (2026-07-24)

Mirror aus [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) Single-Source-of-Truth. Wird gepflegt
sobald sich Werte ändern — bei Drift wird `bash tools/phase47-shield.sh` rot.

| Metrik | Wert | Herkunft |
|--------|------|----------|
| EconomySim LOC | **1.442** | `wc -l src/.../core/EconomySim.java` (von 1.553 reduziert) |
| Java-Dateien in core/ | **96** | `find src/.../core -name '*.java' \| wc -l` |
| EngineSeams-Direkt-Calls in core/ | **34** | `grep -rE 'EngineSeams\\.[a-z]+(' core/` |
| IdentityHashMap-Dateien in core/ | **10** | `grep -rln 'new IdentityHashMap' core/ \| grep -v IdentityMapRegistry` |
| `catch (Throwable)` in core/ | **2** | `grep -rE 'catch \\(Throwable' core/` |
| `printStackTrace()` in core/ | **0** | `grep -rE 'printStackTrace\\(\\)' core/` |
| TreasuryCrisis-Tiers | **5 + Hard Floor** | `TreasuryCrisis.java` |

**Heuristik-Werte** haben im Vergleich zu **Konstanten-Werten** in §1–§6 andere Lebenszyklen:
Konstanten werden in v0.x-Releases explizit geändert (Changelog-Eintrag), Heuristik-Werte
passen sich mit jeder Code-Änderung an. Der Truth-Drift-Test im `phase47-shield.sh` fängt
es ab, sobald eine Metrik ihren Threshold reißt.
