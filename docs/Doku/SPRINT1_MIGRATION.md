# Sprint 1 — StartingFromGround (Migration & Cap)

**Audience:** UI-Toggle-Subteam (Sprint v0.13.110+ Phase-2-Gate — Pop-Cap +5 nach Handelspartner).

**Zweck dieses Dokuments:** Kontext für den UI-Schalter "meticImmigrationCap +5". Welche
Werte stehen warum auf 0/0.20/50, in welcher Phase schalten sie frei, und wie merkt der
Spieler dass der Cap greift?

---

## 1. Was ist Sprint 1 — StartingFromGround?

Sprint v0.13.108+StartingFromGround ist der **Soft-Reset-Sprint**: das Spiel startet
nicht mehr mit 200 000 D Treasury-Reserve sondern mit **0 D Startkapital**. Der Spieler
muss selbst Einnahmen generieren (Steuern, Marktverkäufe, Lohneinkommen der Bürger)
bevor er investieren kann.

**Symptom (vor Sprint 1, ist klar dokumentiert in `Doku/CHANGELOG.md` v0.13.107):**
"Stille Geldschöpfung mit startingTreasury=200k → Gini driftete unkontrolliert Richtung
1.0 (in Playtests: Gini 0.95 bei Treasury -900M als sichtbares Symptom)."

**Fix:** Treasury fängt bei 0 an; Bürger starten mit eigenem Wallet; Migrations-Booster
ist gedrosselt; ein **Cap von 50 Siedlern** verhindert Erst-Spike.

---

## 2. Die drei Phasen

### Phase A — Start (Tag 0 → Pop 0-50)
- **Treasury: 0 D** (kein staatliches Startkapital)
- **First-Settler-Buff:** Wallet der ersten ~50 Bürger enthält zusätzlich
  `earlySettlerWalletBonus` (z.Z. 0 D nach Sprint v0.13.108+StartingFromGround Comment)
- **Migrations-Depth sehr klein:** `0.20` (vorher 0.35) — Eintreffenswahrscheinlichkeit
  ist eng, fast keine Spike-Immigration
- **Bootstrap GrainDole:** ALLE Bürger bekommen gratis Essen solange
  `population < earlySettlerPopThreshold=50` — durchbricht die Thron-Bug-Pleite-Spirale

### Phase B — Migrations-Cap (Pop 50)
- **Hard cap:** `meticImmigrationCap = 50` — Vanilla-Engine akzeptiert keine weiteren
  Immigranten solange Cap voll ist (Booster-Wert wird auf 0.5 = neutral gesetzt)
- **Spieler muss aktiv eine UI-Aktion auslösen** um den Cap anzuheben — Default ist
  STICKY-CAP bis expliziter UI-Klick

### Phase C — Phase-2-Gate (Pop > 50, Handelspartner vorhanden)
- **Trigger:** Erster Handelspartner-Vertrag abgeschlossen UND Lagerhaus gebaut
- **Aktion:** UI stellt Toggle "Migrations-Cap +5" zur Verfügung — Spieler hebt
  Cap inkrementell an (50 → 55 → 60 → ...)
- **Begleitende Anpassung:** `meticImmigrationDepth` kann von 0.20 auf 0.35 erhöht
  werden sobald `Stage.HANDEL` (EconProgression) erreicht ist

---

## 3. Tuning-Konstanten — Quick Reference

| Konstante | Datei : Zeile | Default | Typ | Phase A wirksam? | Phase B wirksam? | Phase C wirksam? |
|---|---|---|---|---|---|---|
| `startingTreasury` | `EconConfig.java` : 257 | **0 D** (war 200000) | int | ✅ | ✅ | ✅ |
| `earlySettlerBuffEnabled` | `EconConfig.java` : 477 | true | bool | ✅ | ❌ (auto-off bei pop≥50) | ❌ |
| `earlySettlerWalletBonus` | `EconConfig.java` : 483 | **0 D** (war 500) | int | ✅ | ❌ | ❌ |
| `earlySettlerDoleThreshold` | `EconConfig.java` : 491 | 10000 D | int | ✅ | ❌ | ❌ |
| `meticImmigrationDepth` | `EconConfig.java` : 406 | **0.20** (war 0.35) | double | ✅ | ✅ | ✅ (kann auf 0.35) |
| `meticImmigrationSteepness` | `EconConfig.java` : 407 | 10.0 | double | ✅ | ✅ | ✅ |
| `meticImmigrationCap` | `EconConfig.java` : 408 | **50** (NEU) | int | ❌ (cap nicht aktiv) | ✅ (cap aktiv) | ❌ (user-anhebbar) |
| `population` (T8) | `EconConfig.java` : 502 | live | int | ✅ | ✅ | ✅ |

**Komplett-Tabelle bezieht sich auf:** `src/vannon/syx/economy/core/EconConfig.java`
zeilen 257–502 (siehe commit `5786390` Sprint v0.13.108+Doku-Slim Backend).

---

## 4. Wie merkt der Spieler, dass der Cap erreicht ist?

### Visuelle Indikatoren (im Spiel sichtbar)
1. **MeticImmigration-Fenster / EventLog:** "Cap 50 erreicht — keine neuen
   Immigranten mehr bis Spieler Cap anhebt oder Handelspartner/Lagerhaus baut."
2. **HUD-TopBar:** Population-Anzeige bleibt bei 50 hängen; ggf. roter Warn-Indikator.
3. **QuickView-Tab:** `Bevoelkerung`-KPI unverändert; "Tote/Ausgewandert"-KPI zeigt
   Emigration-Rate steigt weil Bürger nicht wandern können.

### Technischer Indikator (im Code)
- `meticImmigration.register()` setzt Immigration-Booster auf 0.5 (neutral)
- Vanilla-Engine wertet Booster-Wert ≤ 0.5 als "kein Pull" → keine neuen Settler
- Settler-Counter im Engine-State stagniert

### Auswirkung auf andere Systeme
- **EconProgression.Stage.INDUSTRIE braucht Pop≥150** — mit Cap=50 unmöglich;
  Spieler muss Cap anheben bevor er Wohlstand-Stufe erreichen kann
- **EconProgression.Stage.HANDEL braucht Pop≥75** — mit Cap=50 ebenfalls blockiert
- **Daher:** Cap-Anhebung ist der **Schlüssel-mechanische Schritt** zwischen
  StartingFromGround und normaler Vanilla-Economy-Progression

---

## 5. UI-Toggle-Spec für 'meticImmigrationCap +5 nach Handelspartner'

### Kontext fürs UI-Team
- Toggle ist **kein Auto-Unlock** — Spieler entscheidet bewusst
- Position: vermutlich im **Rat-/Advisor-Tab** neben "Migrations-Politik"
- Default-Wert: 50 (= aktueller Cap), per Slider 50..500 in 5er-Schritten

### Aktion bei Klick
```java
// Pseudo-Code, wie der UI-Klick den State aendert
EconConfig.meticImmigrationCap += 5;
MeticImmigration.notifyCapRaised();   // re-checkt Booster-Wert naechsten Tick
EventLog.log("UI", "Migration-Cap erhoeht auf " + EconConfig.meticImmigrationCap);
```

### Trigger-Block (UI-Anleitung)
> "Solange Cap=50 blockiert: kein INDUSTRIE-Stage möglich (Pop≥150 erforderlich).
> Sobald dieser Toggle aktiv ist UND der Spieler einen Handelspartner hat UND ein
> Lagerhaus baut, schaltet die Engine-Migration stufenweise frei."

### Guard-Clauses im UI-Code
- ❌ Toggle soll **disabled/greyed** sein wenn:
  - Stage < HANDEL (player hasn't proven trading capability)
  - Kein Lagerhaus gebaut (kein infrastruktureller Anker)
- ✅ Toggle soll **enabled** sein wenn:
  - Stage ≥ HANDEL (EconProgression.stage.level >= 1)
  - `sim.stateWarehouses().ownedCount() >= 1`
  - **UND** ein Handelspartner vorhanden (PolityPriceAnchor.hasTradePartner() == true)

---

## 6. sim-default

**Aktueller Default-Stand (commit 5786390):**
- **startingTreasury = 0**
- **meticImmigrationDepth = 0.20**
- **meticImmigrationSteepness = 10.0**
- **meticImmigrationCap = 50**
- **earlySettlerBuffEnabled = true**
- **earlySettlerPopThreshold = 50**
- **earlySettlerDoleThreshold = 10000**
- **earlySettlerWalletBonus = 0**

Die Default-Werte spiegeln den StartingFromGround-Zustand nach Sprint v0.13.108.
Pre-Sprint-Werte (vorher) wären: startingTreasury=200000, depth=0.35,
walletBonus=500. Diese sind jetzt via `populateInitialize()`-Doku im Code markiert.

---

## 7. Wo finde ich was im Repo?

| Was | Pfad : Zeilen |
|---|---|
| StartingFromGround-Tuning | `src/vannon/syx/economy/core/EconConfig.java` : 257, 406-408, 477-491, 502 |
| Migrations-Booster-Mechanik | `src/vannon/syx/economy/core/MeticImmigration.java` : 41-53 (Cap-Logik) |
| Stage-Trigger (Pop-Schwellen) | `src/vannon/syx/economy/core/EconProgression.java` : 138-180 |
| Trade-Partner-Gate | `src/vannon/syx/economy/core/PolityPriceAnchor.java` : 72 (hasTradePartner) |
| Doku-Referenz | `Doku/CHANGELOG.md` (v0.13.107 + StartingFromGround-Einträge) |
| Governance-Decision | `docs/GOVERNANCE_DIAT_2026-08-01.md` (Sprint-1 Sektion) |
| Session-Handover | `docs/2026-07-31_SESSION_HANDOVER.md` |

---

## 8. Sprint-Sequenz für Phase-2-Gate (Cap-Anhebung)

```
Tag 0:      startTreasury=0, Pop=0-50 (Cap nicht aktiv)
            ├─ Phase A: earlySettlerBuff (alle Bürger kriegen gratis Korn)
            └─ Phase A: Migrations-Depth=0.20 (sehr enge Eintreffens-Wahrscheinlichkeit)

Pop=50:     Phase B: Cap aktiv
            ├─ Vanilla-Engine akzeptiert keine weiteren Immigranten
            ├─ Toggle "meticImmigrationCap +5" wird im Advisor-Tab sichtbar
            └─ Disabled-State: noch kein Handelspartner

First Trade Partner + 1. Lagerhaus:
            Phase B-Übergang: Toggle wird ENABLED
            ├─ range 50 .. 500 (5er-Schritte)
            ├─ klick: EconConfig.meticImmigrationCap += 5
            └─ EventLog-Eintrag "Migration-Cap erhöht auf X"

Cap=150:    INDUSTRIE-Stage erreichbar (EconProgression.checkAdvance)
Cap=500:    WOHLSTAND-Stage erreichbar
            Phase C abgeschlossen — voller Mod aktiv
```

---

## 9. Test-Cases für die Migration-Cap-Logik (Sprint U3+ Test-Hooks)

```java
// Wirtschaftliche Phase-Population-Blackbox-Test
@Test void pop50_blocks_additional_settlers() {
    sim.setPopulation(50);
    assertFalse(MeticImmigration.canAcceptNewSettler());  // Cap=50, Booster=neutral
}

@Test void first_trade_partner_lifts_cap_lock() {
    sim.setPopulation(50);
    PolityPriceAnchor.activateTradePartner();
    assertTrue(MeticImmigration.canAcceptNewSettlerAfterCapRaise());
}

@Test void cap_raise_increments_by_5() {
    EconConfig.meticImmigrationCap = 50;
    uiToggleMigrationsCapPlus5.click();
    assertEquals(55, EconConfig.meticImmigrationCap);
}
```

---

## 10. Kontakt-Personen für Rückfragen

- **Tuning-Konstanten:** Code-Owner siehe `Code-Reviewer`: Sprint v0.13.108+Doku-Slim
  Commit Hash `5786390`
- **Migrations-Logik-Booster:** MeticImmigration.java (siehe Code-Snippet oben)
- **UI-Anleitung / Toggle-Klick-Aktion:** UI-Toggle-Subteam (siehe `docs/2026-07-26-onboarding-player-agency.md`)
