# SyxEconomyMod

> **Wirtschaftssimulation für Songs of Syx V71.**
> Bürger verdienen Löhne, zahlen Steuern, kaufen Nahrung, mieten Wohnungen.
> Firmen maximieren Profit, stellen Arbeiter ein. Der Staat führt die Kasse — mit Krisenmechanik.

**Version:** v0.13.1-alpha | **Spiel:** V71.44 | **Java:** 21

---

## Quickstart

```bash
mvn compile          # bauen
mvn package          # JAR + Assets + _Info.txt
# → target/out/SyxEconomyMod/ ins Mod-Verzeichnis kopieren
```

---

## Was der Mod macht

| System | Kern |
|---|---|
| **Arbeitsmarkt** | Firmen bieten Löhne, Arbeiter priorisieren nach Profitabilität |
| **Geldumlauf** | Jeder Bürger hat ein Wallet — verdient, kauft, spart, vererbt |
| **Marktpreise** | Supply/Demand → Scarcity-Signale pro Ressource |
| **Steuern & Staat** | Kopfsteuer, Marktsteuer, Religion, Liturgie — mit Freigrenzen |
| **Staatskasse** | 5-stufige Treasury-Crisis mit Hard Floor |
| **Vermögen** | Gini-Koeffizient, Property-Markt, Miete, Dividenden |
| **UI** | 4 Fenster (Übersicht, Wirtschaft, Staat, Quickview), 15 Tabs |

---

## Struktur

```
src/vannon/syx/economy/
├── core/        ← 98 Dateien, ~21k LOC (EconomySim: ~1.4k)
├── adapter/     ← Vanilla-API-Abstraktion (17 Dateien)
├── ui/          ← 5 Fenster: Overview, Economy, State, Quickview, Base
└── settlement/  ← Bridge-Klassen für SoS-Raum/Service-Hooks
```

---

## Dokumentation

| Datei | Inhalt |
|---|---|
| [`CHANGELOG.md`](CHANGELOG.md) | Vollständige Release-Historie |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Architektur, Datenfluss, Klassen-Übersicht |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | TODO & Phasenplanung |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Live-Test-Funde, Bugs |
| [`docs/GLOSSARY.md`](docs/GLOSSARY.md) | 112 Klassen-Glossar |

---

## Tools

```bash
tools/
├── build-gate.sh                  # Pre-Build: Audit + Version-Check
├── verify-version-consistency.sh  # pom.xml ↔ CHANGELOG.md
├── phase47-shield.sh              # CI-Gate für kritische Blocker
├── code-audit.sh                  # IdentityHashMap, catch(Throwable), printStackTrace
├── truth-stamp.py                 # Commit-Hook: Docs auf Aktualität prüfen
└── install-hooks.sh               # Git-Hooks installieren
```

---

## Credits

- **Original:** TiredGirl4's Economy Mod
- **Entwicklung:** vannon091118
- **Engineering:** Freebuff-assisted

