# Design Spec: Onboarding & Player Agency (v0.14.0)

> **Datum:** 2026-07-26  
> **Status:** Genehmigt (User Live-Test Feedback)  
> **Ziel:** Sofortige Eingriffsmöglichkeiten für Spieler in den ersten 10 Minuten + Interaktives Popup-Tutorial + 1-Tab-Steuerzentrale.

---

## 1. Problemstellung & Lösungskonzept

### Problem
Im bisherigen Design sieht der Spieler viele wirtschaftliche Zustände (Tabellen, Ampeln, Verläufe), hat jedoch zu wenige direkte Steuerhebel im ersten Tab. Zudem sind manche Einstellungen hinter Sub-Tabs oder Default-False-Flags verborgen.

### Lösung
1. **1-Tab-Steuerzentrale ("Steuerung & Übersicht"):** Alle Kernhebel (Löhne, Steuern, Staatslager-Modi, Not-Liquidation) befinden sich direkt im ersten Tab des Mod-Fensters.
2. **Default-Active:** Keine Kernmechanik ist standardmäßig deaktiviert (`enabled = true` für alle Toggles).
3. **EconHUD Quick-Action Bar:** 4 Schnellzugriffs-Buttons direkt im HUD für Instant-Control.
4. **10-Minuten Onboarding-Tutorial:** Interaktive Popups führen schrittweise durch die Nutzung der Hebel.

---

## 2. Architektur & Komponenten

### 2.1 Tab 1 — Consolidated Control Dashboard (`WindowOverview.java`)
Das Hauptfenster vereint KPIs und Eingriffsinstrumente:
- **Lohnsteuerung:** `LiveSlider` für Staatslöhne & Lagerlöhne.
- **Steuersteuerung:** `LiveSlider` für Kopfsteuer + Globaler Steuer-Toggle (default `true`).
- **Staatslager-Management:** Buttons für Handelsmodus (`Normal`, `Nur Kaufen`, `Nur Verkaufen`) + `[Not-Liquidation]`.
- **Einnahmen & Subventionen:** Ein-Klick-Toggles für Notfall-Hilfen & Liturgie.

### 2.2 EconHUD Quick-Action Bar (`EconHud.java`)
Omnipräsente Schnellzugriffs-Buttons im Spiel-HUD:
1. `[Lohn: X Denari]` — Öffnet Mini-Adjustment.
2. `[Lager: Modus]` — Schaltet Handelsmodus durch.
3. `[Not-Liquidation]` — Sofortiger Notfall-Verkauf aller Staatsgüter.
4. `[Steuern: Aktiv]` — Schnell-Schalter für Steuererhebung.

### 2.3 Onboarding-Tutorial Controller (`EconTutorialController.java`)
Verwaltet den Fortschritt des 10-Minuten-Tutorials:
- **Triggers:** Zeitstempel & Krisen-Events (z.B. unbezahlte Arbeiter).
- **Schritte:**
  1. *Minute 0:10:* Begrüßung & Tageslohn setzen.
  2. *Minute 2:00:* Staatslager aktivieren & Modus wählen.
  3. *Minute 5:00:* Steuern & Staatseinnahmen konfigurieren.
  4. *Minute 10:00:* Krisenmechanik & Not-Liquidation verstehen.

---

## 3. Qualifikation & Self-Review

- **Placeholder Check:** Keine TBDs, alle Schwellenwerte und Komponenten definiert.
- **Konsistenz:** Passt zu Vanilla UI (`GButt`, `GText`, `GPanel`).
- **Scope:** Fokussiert auf UX/UI-Onboarding und Dashboard-Konsolidierung.
