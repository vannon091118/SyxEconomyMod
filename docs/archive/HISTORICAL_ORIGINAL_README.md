# TiredGirl4's Economy Mod – Dekompilierte Quellen

> ⚠️ **HISTORISCH** — Original-README des TiredGirl4-Mods. Für aktuellen Stand siehe ../README.md.

**Spiel:** Songs of Syx
**Workshop-ID:** 3763893174
**Version laut `_Info.txt`:** 1.0.0
**Ziel-Game-Version:** 0.71.44
**Autor:** tiredgirl4

## Ursprung

Die `.jar`-Datei lag unter:

```
/home/vannon/snap/steam/common/.local/share/Steam/steamapps/workshop/content/1162750/3763893174/V71/script/TiredGirl4's Economy Mod.jar
```

Diese Datei wurde mit dem CFR-Decompiler (v0.152) zurück in Java-Quellcode übersetzt und in diesen Ordner kopiert.

## Ordnerstruktur

```
settlement/room/service/food/
  canteen/EconomyCanteenAccess.java
  eatery/EconomyEateryAccess.java
  tavern/EconomyTavernAccess.java

tiredgirl4/economy/
  MainScript.java
  InstanceScript.java
  econ/
    <Kernlogik der Wirtschaftssimulation>
```

## Wichtige Klassen

| Klasse / Gruppe | Bedeutung |
|---|---|
| `MainScript` / `InstanceScript` | Mod-Einstiegspunkte |
| `EconomySim` | Haupt-Tick-Schleife, koordiniert alle Subsysteme |
| `Wallets` | Geld pro Siedler |
| `ExchangeKernel` | Yard-Sale-Geldumverteilung |
| `FlowPrices` / `FlowMeter` | Dynamische Preise nach Angebot/Nachfrage |
| `LocalPrices` | Preise für Nahrung, Getränke, Luxusgüter |
| `AffordabilityGate` | Prüft, ob ein Siedler eine Mahlzeit/Dienstleistung/Ware bezahlen kann |
| `FoodTransactionPlan` / `DrinkTransactionPlan` / `GoodsTransactionPlan` | Ersetzen die Vanilla-AI-Pläne für Essen, Trinken, Einkaufen |
| `ServiceMarket` / `ServicePlanController` | Bezahlte Dienstleistungen |
| `FirmLedger` / `LaborMarket` / `StateWageMarket` | Arbeitsmarkt, Betriebsgewinne, Löhne |
| `WarehouseMarket` / `StateWarehouses` | Handel mit Waren, staatliche Warenhäuser |
| `Taxes` / `Fiscal` / `GrainDole` | Steuern, Staatsfinanzen, Getreidehilfe |
| `DebtBondage` / `CorveeController` / `Liturgy` | Experimentische Mechaniken (standardmäßig deaktiviert) |

## Hinweise

- Der Code ist **automatisch decompiliert**; Variablennamen wie `var1` oder `n` sind daher teilweise kryptisch.
- Viele Features sind in `EconConfig.java` per Standard **ausgeschaltet** (z.B. `wagesEnabled = false`, `taxesEnabled = false`).
- Die Mod greift stark in die Vanilla-Ökonomie ein: Nahrung/Dienstleistungen/Waren kosten Geld, Siedler haben Wallets, und Betriebe passen ihre Größe an den Profit an.

## Decompile-Befehl (zur Nachvollziehbarkeit)

```bash
java -jar cfr.jar \
  /home/vannon/snap/steam/common/.local/share/Steam/steamapps/workshop/content/1162750/3763893174/V71/script/TiredGirl4\'s\ Economy\ Mod.jar \
  --outputdir /tmp/sossecon_src
```

---
*Erstellt am 21.07.2026*
