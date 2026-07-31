# 🏛️ SyxEconomyMod

> **Version:** v0.13.106 | **Songs of Syx** V71.44

**Deine Bürger haben jetzt Geldbeutel. Deine Firmen haben Bilanzen. Dein Staat hat ein echtes Budget — und kann pleitegehen.**

SyxEconomyMod verwandelt Songs of Syx von einer Siedlungssimulation in eine Volkswirtschaft. Jeder Bürger verdient, spart, kauft und stirbt arm oder reich. Jede Firma kämpft um Profit. Und du als Herrscher musst die Staatskasse am Laufen halten — oder den Kollaps miterleben.

---

## 💰 Was sich ändert

**Bürger mit Wirtschaftsleben**
Jeder deiner Bürger hat ein persönliches Konto. Er geht arbeiten, bekommt Lohn, zahlt Steuern, kauft Essen auf dem Markt, mietet eine Wohnung. Erbt von Verwandten. Wird arm — oder reich. Du siehst alles: Gini-Koeffizient, Vermögensverteilung, Bürgerklassen.

**Firmen mit Gewinn und Verlust**
Farms, Schmieden, Tavernen, Werkstätten — alle haben ein echtes Konto. Wenn sie Gewinn machen, zahlen sie Löhne und Steuern. Wenn sie Verlust machen, wandern Arbeiter ab. Preise entstehen aus Angebot und Nachfrage, nicht aus Fixwerten.

**Staat mit Endlosschleife... nein, mit Ende**
Steuereinnahmen minus Militärlöhne minus Subventionen minus Kornverteilung = dein Saldo. Wenn die Kasse leer wird, gibt es 5 Krisen-Stufen — von "Warnung" bis "Alles aus, Loyalty −50%, Bürger fliehen".

**Wirtschaftsstufen — deine Zivilisation wächst**
Von der Subsistenz-Dorf bis zum Imperium: Jede Stufe schaltet neue Mechaniken frei. Privatisierung, Aktienhandel, Metic-Immigration, Admin-Boosts. Fortschritt wird sichtbar.

---

## ⚔️ Was du tust

🎮 **5 Fenster, 16 Tabs** — Überblick, Wirtschaft, Staat, Quickview. Alles per Hotkey erreichbar.

📊 **Berater-Tab** — Sagt dir, was schiefgeht: "Zu wenig Brot", "Steuern zu hoch", "Holz-Export jetzt!"

⚙️ **200+ Schieberegler** — Löhne, Steuern, Kornverteilung, Preisaktualisierung, alles justierbar. Balance für jeden Spielstil.

🏦 **Krisenmechanik** — 5 Stufen von "gemütlich" bis "Staatspleite". In Stufe 5 ist alles aus. Rette dich durch neue Einnahmen oder radikale Sparmaßnahmen.

🏘️ **Immobilienmarkt** — Ab Wohlstand: Bürger kaufen Häuser, kassieren Miete. Ab Imperium: Aktienanteile an Betrieben.

🍞 **Grain Dole** — Kostenlose Kornverteilung für die Ärmsten. Retten oder ruinieren — je nach Budget.

---

## 📦 Installation

**Brauchst:** Songs of Syx V71.44 + Java 17+

**Schnellste Methode — vorgefertigtes Release:**
1. Lade die neueste `.zip` von der **Releases-Seite** herunter
2. Entpacke nach:
   - **Linux:** `~/.local/share/songsofsyx/mods/SyxEconomyMod/V71/`
   - **Windows:** `%APPDATA%\songsofsyx\mods\SyxEconomyMod\V71\`
3. Im Spiel: **Mods → SyxEconomyMod aktivieren → Neues Spiel**

**Selbst bauen (für Entwickler):**
```bash
mvn clean install -DskipTests
cp -r target/out/SyxEconomyMod <Mod-Pfad>
```

---

## 🎯 Tipps für den Anfang

- 📉 **Steuern zu hoch?** → Bürger werden arm, wandern aus. Starte mit niedrigen Steuern.
- 🍞 **Grain Dole aktivieren** → Retten die Ärmsten in der Frühphase. Kostet nichts, schafft Loyalität.
- 🏭 **Betriebe bauen** → Ohne Firmen keine Löhne. Ohne Löhne kein Geld. Ohne kein Steuern. Ohne Steuern... pleite.
- 📊 **Quickview checken** → Zeigt Gini, Kasse, Stufe, Nahrungslage auf einen Blick.
- ⚠️ **Kasse im Auge behalten** → Wenn sie unter −5.000 D fällt, beginnt die Krisen-Kaskade.

---

## 🧰 Technisches (für Modder)

- Vanilla-konform — keine Vanilla-Klassen verändert, Zugriff über Adapter
- 163 Java-Dateien, ~31.000 LOC
- 402 Tests, 11 Gatess, automatisierte Balance-Regression
- Save/Load abwärtskompatibel (Chunked-Format v33)
- Vollständige Architektur-Doku: [`ARCHITECTURE.md`](ARCHITECTURE.md)

---

## 👥 Credits

- **Original:** TiredGirl4's Economy Mod
- **Entwicklung:** vannon091118
- **Engineering:** Freebuff-assisted

---

> **Version:** v0.13.106 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-31
