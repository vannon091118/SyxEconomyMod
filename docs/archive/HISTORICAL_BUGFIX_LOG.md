# SyxEconomyMod: Systemanalyse, Bug-Report & Fix-Dokumentation

> ⚠️ **HISTORISCH** — Stand: Juli 2026. Dieses Dokument beschreibt behobene Bugs aus früheren Versionen. Für aktuellen Stand siehe COVERAGE_AUDIT.md und CHANGELOG.md.

## Executive Summary
In dieser Prüfung wurde die Funktionsweise der **SyxEconomyMod** gegen die Songs of Syx Vanilla API (v71.44) analysiert, unbehandelte/totlaufende Systemteile identifiziert, API-Inkompatibilitäten behoben und die Benutzeroberfläche (Snake2D Engine) für unzureichend angebundene Subsysteme (Miet-System, Chronik) vollständig erweitert.

---

## 1. Systemanalyse: Wirkung der Aktionen & Mod-Hebel in Songs of Syx

Die Mod greift tiefergehend in die Spielmechanik von Songs of Syx ein, als standardmäßige Wirtschafts-XMLs erlauben. Sie ersetzt bzw. überlagert das staatliche Planwirtschaftsmodell durch ein mikroökonomisches Modell:

| Mod-Hebel / Subsystem | Interne Funktionsweise | Wirkmechanismus im Vanilla-Spiel (Songs of Syx) |
| :--- | :--- | :--- |
| **Brieftaschen & Börse** (`Wallets`, `ExchangeKernel`) | Jeder Siedler besitzt ein individuelles Guthaben (Denari) und tauscht Geld bei Begegnungen via kinetischer Umverteilung. | Steuert die Kaufkraft der Bevölkerung. Wohlstand erzeugt direkt Zufriedenheit via `WealthHappiness`. |
| **Miet- & Immobilienmarkt** (`HousingMarket`, `PropertyLedger`) | Berechnet saisonale Mieten je Haus/Kammer (berechnet aus Fläche & Isolation). Siedler zahlen Miete aus eigener Brieftasche. | **High Impact**: Bei Zahlungsverzug & Ablauf der Schonfrist greift `STATS.HOME().GETTER.set(occupant, null)` — der Siedler verliert sein Haus in Songs of Syx und wird obdachlos! |
| **Schuldenknechtschaft** (`DebtBondage`) | Übersteigt die Verschuldung eines Bürgers den Schwellenwert (default 5000 Denari), greift das System ein. | **High Impact**: Ausführung von `EngineSeams.enslave(humanoid)`. Der freie Bürger wird in der Vanilla-Engine direkt zum Sklaven (`HTYPES.SLAVE()`) umgewandelt! |
| **Bedürfnis-Gates** (`AffordabilityGate`, `FoodPlanController`, `ServicePlanController`) | Vor dem Konsum an Cantina/Eatery/Taverne prüft das Gate, ob der Bürger die Mahlzeit/den Trank bezahlen kann. | Kann ein Bürger nicht zahlen und greift keine Kornspende (`GrainDole`), wird die Konsumaktion verweigert (latente Nachfrage / Hunger). |
| **Dynamische Arbeitsmarkt-Priorität** (`LaborMarket`) | Berechnet die Profitabilität & Knappheit von Betriebserzeugnissen und passt die Raum-Prioritäten an. | Überschreibt dynamisch die Arbeitsplatzpriorität (`RoomBlueprintImp`) im Siedlungssystem, damit Bürger bevorzugt in Mangelbetrieben arbeiten. |
| **Fiskalpolitik & Staatskasse** (`Taxes`, `Fiscal`, `StateWages`) | Steuern, Marktabschöpfungen und Immobilienverkäufe fließen in die Staatskasse (`FACTIONS.player().credits()`). | Lohnzahlungen und Subventionen buchen direkt von den Credits der Spielerfraktion ab. Leere Staatskasse führt zu Lohnstopp. |

---

## 2. Bug-Report: Gefundene Inkompatibilitäten, unvollständige Verkabelungen & "Tote" UI-Elemente

### 🔴 Bug 1: Kompilierungsfehler in `AffordabilityGate.java` (API-Inkompatibilität)
- **Fehler**: `humanoid.indu().name()` führte zu einem Symbol-Not-Found Compilerfehler (`Induvidual` besitzt in Vanilla v71.44 keine `.name()` Methode).
- **Ursache**: Aufruf einer nicht existierenden Methode auf der `Induvidual`-Klasse.
- **Lösung**: Aufruf auf die valide Vanilla-Klasse `Humanoid.title()` umgestellt.

### 🔴 Bug 2: "Totes UI-Fenster" Chronik (`EventLog.java` & `BOOKS` Tab)
- **Fehler**: Das `EventLog` schrieb Logs ausschließlich in eine externe Datei `economy_events.log` (sofern `debugPriceLogging` aktiv war). Die Chronik im UI (`BOOKS`-Tab / `EconTexts.¤¤historyHeader`) zeigte keinerlei Live-Ereignisse an.
- **Ursache**: Es existierte kein In-Memory-Ringpuffer im `EventLog`, und der UI-Tab führte keine Darstellung von Ereignissen aus.
- **Lösung**: In `EventLog.java` wurde ein thread-sicherer Ringpuffer (`recentEvents`, max. 100 Einträge) hinzugefügt. Im `BOOKS`-Tab von `EconomyWindow.java` wurde ein dynamisches Chronik-Ereignisfenster mit Farbkategorisierung (Miete, Schulden, Konsum, Eigentum) integriert.

### 🔴 Bug 3: "Miet-System ingame unsichtbar" & Unverkabelte Parameter
- **Fehler**: Das voll funktionierende `HousingMarket`-Subsystem (Mieteinnahmen, Räumungen, Schonfristen, Immobilienkäufe) besaß im UI lediglich eine einzige statische Textzeile ohne Jegliche Steuerungs- oder Einstellmöglichkeiten.
- **Ursache**: Schalter und Slider für `housingBaseRentPerTile`, `housingEvictionDebtThreshold`, `housingGraceDays`, `housingMarketEnabled`, `propertyMarketEnabled` und `homePurchaseEnabled` fehlten in `EconomyWindow.java`.
- **Lösung**: Der `CITIZENS`-Tab in `EconomyWindow.java` wurde zu einem vollständigen **Bürger- & Mietmarkt / Immobilien-Panel** erweitert inklusive aller Schalter, Slider, Eingabefelder und Echtzeit-Statistiken (Einnahmen, Räumungen, Hausverkäufe, Gilden-Dividenden).

---

## 3. Durchgeführte Fixes & Code-Änderungen

1. **`src/vannon/syx/economy/core/AffordabilityGate.java`**
   - Syntaktischen Aufruf `humanoid.indu().name()` durch den validen API-Aufruf `humanoid.title()` ersetzt.

2. **`src/vannon/syx/economy/core/EventLog.java`**
   - Datenstruktur `EventEntry` (Kategorie, Nachricht, Zeitstempel) implementiert.
   - Ringpuffer `recentEvents` (max. 100 Ereignisse) mit `getRecentEvents()` und `clearRecentEvents()` hinzugefügt.

3. **`src/vannon/syx/economy/core/HousingMarket.java` & `DebtBondage.java`**
   - EventLog-Triggers bei kritischen Lebensereignissen hinzugefügt (Räumung wegen Mietrückständen, Verkauf in die Schuldenknechtschaft, Hauskäufe).

4. **`src/vannon/syx/economy/core/EconomyWindow.java`**
   - `renderCitizens`: Komplettes Bedienfeld für das Miet- & Immobiliensystem mit 3 Schaltern (Miete einziehen, Immobilienmarkt AN/AUS, Hauskauf erlaubt), 3 Slidern mit Wertefeldern (Miete/Kachel, Räumungsschwelle, Schonfrist) und Finanz-Statistiken hinzugefügt.
   - `renderBooks`: Live-Chronik-Sektion am Ende der Bücheranzeige eingebunden, die die letzten Ereignisse farblich hervorgehoben rendert.

---

## 4. Build- & Verifikationsstatus

- **Compilation (`mvn compile`)**: `BUILD SUCCESS` (0 Fehler, 82 Quelldateien kompiliert).
- **Packaging (`mvn package`)**: `BUILD SUCCESS` (Generiertes Shaded-JAR: `target/SyxEconomyMod.jar`).
- **Engine-Kompatibilität**: Getestet gegen `SongsOfSyx.jar` v71.44 (Java 21/25 Bytecode-Kompatibilität).
