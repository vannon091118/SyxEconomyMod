# Backlog (New-Findings Inbox)

> **Auto-managed Inbox.** Entries flow in from:
> - `tools/consolidate-live-notes.sh` — `gap:net-new` and `ux:papercut` tags
> - Manual backlog triage during plan reviews
>
> **Master-Task-Liste: [`ROADMAP.md`](../ROADMAP.md)** — alle T-/B-IDs werden
> dort konsolidiert (per agents.md Rule 13, ab v0.13.31).
> Diese Datei dient nur als **Inbox für neue Live-Findings**, die noch
> nicht im ROADMAP-GlobalIndex sind. Sobald ein Finding in die ROADMAP
> aufgenommen wird (T- oder B-ID vergeben), wird der Eintrag hier gelöscht.
>
> Format: `### [ID] Title` with Source, Severity, Status. Severity:
> 🔴 P0 (crash/data-loss) · 🟠 P1 (broken feature) · 🟡 P2 (papercut) · 🟢 P3 (nice-to-have).
>
> **Verschieb-Verbot aktiv** (agents.md Rule 13): Keine "Verschoben" / "Postponed" / "Deferred" / "Next-Sprint"-Markierungen. Tasks sind entweder hier (neu, nicht im Sprint-Plan) oder in der ROADMAP (Planned/Active/Closed/Rejected).

---

## Sprint-Snapshot

Letzter abgeschlossener Sprint: **Sprint 3 — Roadmap-SSOT-Konsolidierung + P1-Blocker-Closure** (7 Tasks T14.0–T14.6).

Backlog-Master-Liste: [`ROADMAP.md § Planned Backlog`](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+).
T-IDs (Sprint-Tasks) und B-IDs (Live-Findings) sind dort zentral verwaltet. Diese Datei hier dient nur als Inbox für noch-nicht-erfasste Findings.

---

## New-Findings Inbox (Livetest v0.13.64 + Vanilla-Analyse, 26.7.26)

### Live Findings (aus EventLog + Screenshots + Trace-Analyse)

| ID | Severity | Finding | Source | ROADMAP |
|---|---|---|---|---|
| **B-011** | 🔴 P0 | AccessAutomation permanent deaktiviert: `accessDetectionDisabled` (static) bei erster Exception=true, kein Mid-Session-Reset. Housing-Einrichtungsziele tot. | EventLog `[ACCESS] AccessAutomation room scan disabled` | ROADMAP → Sprint B-FIX |
| **B-012** | 🟠 P1 | EconProgression 3850 Tage in Subsistenz: checkAdvance() blockiert durch fehlende Taverne/Labor. | Screenshot: Stufe Subsistenz seit 3850 Tagen | ROADMAP → Sprint B-FIX |
| **B-013** | 🟡 P2 | Advisor empfiehlt Export bei Stone 75.3x/Wood 73.7x Preis. Preisdaten nicht mit Advisor verknüpft. | Screenshot: Berater-Tab vs Preise-Tab | ROADMAP → Sprint B-FIX |
| **BA-01** | 🟠 P1 | -1.8M Treasury bei 37 Siedlern, 5 Arbeitern — Lohn/Subventions-Spirale. | Screenshot: Quickview -19M (Display-Bug) | ROADMAP → BA |
| **BA-02** | 🟡 P2 | Gini 0.946: 3 Ausreißer (333K/500K/1.3M) vs 34 Bürger mit Median 4D. | Screenshot: Demografie-Tab | ROADMAP → BA |
| **BA-03** | 🔴 P0 | Arbeitslosigkeits-Todesspirale: Broke→Starve statt Broke→Oddjob→Arbeit→Geld→Essen. | EventLog `[LATENT_DEMAND] Food purchase rejected` | ROADMAP → Sprint L-1 (L-01) |

### UI Papercuts (aus Livetest Screenshots)

| ID | Severity | Finding |
|---|---|---|
| **U-01** | 🔴 P0 | Quickview -19M D vs Dashboard -1.9M D — CompactNumber/Treasury-Snapshot-Drift |
| **U-02** | 🔴 P0 | GText-Overflow `####-500D#` im Kopfsteuer-Feld |
| **U-03** | 🟠 P1 | Demografie-Tabelle: 37 Siedler, 0 Zeilen — CitizenClass.render() leer |
| **U-04** | 🟡 P2 | Firmen-Tab: FARM_GRAIN/FISHERY_NORMAL statt lokalisierter Namen |
| **U-05** | 🟡 P2 | Ampel-Pfeile inkonsistent |
| **U-06** | 🟡 P2 | Berater-Text mid-sentence abgeschnitten |
| **U-07** | 🟡 P2 | Religionssteuer-Label-Overflow (x+300 vs x+308) |
| **U-08** | 🟡 P2 | Onboarding SCHRITT 4/4 überlebt Krisenzustand |
| **U-09** | 🟠 P1 | Bücher-Tab: "Kasse + Umlauf = 203.3K D" widerspricht -1.8M Treasury |

### Vanilla-Lücken (aus Source-Analyse 26.7.26)

| ID | Severity | Finding | Vanilla-Source |
|---|---|---|---|
| **DIPLO-01** | 🔴 P0 | Opinion/Trust-Mechanik komplett ignoriert: ROPINION.trust() nur lesend, kein Write. bOpinion/TRUST ungenutzt. | `ROPINION.java`, `DipWarPlayer.java` |
| **L-02** | 🟠 P1 | Fatigue/STAMINA ungenutzt: BOOSTABLES.PHYSICS().STAMINA existiert, Mod hat 0 Code. | `BOOSTABLES.java:physics.STAMINA` |
| **DOC-02** | 🟡 P2 | BINDUNGSMATRIX J1-J6: StatsBehaviour→StatsMultipliers (Vanilla-verifiziert). | `StatsMultipliers.java` |

### TODO/FIXME im Code (2 offen, Stand 26.7.26)

| Datei:Zeile | Marker | Beschreibung |
|---|---|---|
| `FactionAccessImpl.java:418` | `// TODO: TradeManager.tarif(Faction, Faction, TRADABLE, int)` | Vanilla-Tarif-Methode noch nicht via BypassGate angebunden |
| `FlowMeter.java:409` | `* Echte Intent-Gap-Berechnung ... ist TODO` | employeesNeeded/employeesActual-Berechnung fehlt |

---

## Historische Einträge (vor v0.13.31, archiviert)

---

## Historische Einträge (vor v0.13.31, archiviert)

Vor v0.13.31 enthielt diese Datei eine Master-Task-Liste mit B-001..B-011.
Diese wurden in den Sprint 2 / Sprint 3 migriert und sind jetzt in der
ROADMAP als Planned-Backlog konsolidiert (siehe oben). Die alte Form
dieser Datei wird durch die aktuelle New-Findings-Inbox ersetzt.