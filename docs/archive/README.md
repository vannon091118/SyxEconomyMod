# 📦 docs/archive/ — Eingefrorene Snapshots

> **Dieses Verzeichnis enthält ausschließlich historische Stand-Snapshots.** Sie sind eingefroren
> und werden **nicht mehr gepflegt**. Wer aktuelle Aussagen sucht, ist hier falsch.

---

## Was hier liegt

| Datei | Eingefroren am | Inhalt | Stand vor |
|-------|----------------|--------|-----------|
| `HISTORICAL_BUGFIX_LOG.md` | 2026-07 (vor v0.1.0) | Bug-Report + Fix-Dokumentation aus früher Session | [docs/CHANGELOG.md](../CHANGELOG.md) im Root |
| `HISTORICAL_FULLSCAN.md` | 2026-07-21 (vor v0.1.0) | TiredGirl4-Decompile-Scan vs. Vanilla-API | [docs/reports/COVERAGE_AUDIT_FINAL_2026-07-23.md](../reports/COVERAGE_AUDIT_FINAL_2026-07-23.md) |
| `HISTORICAL_ORIGINAL_README.md` | 2026-07 (vor v0.1.0) | Original-README des TiredGirl4-Mods | [../../README.md](../../README.md) |
| `HISTORICAL_ORIGINAL_INFO.txt` | 2026-07-21 (vor v0.1.0) | Original-`_Info.txt` des TiredGirl4-Mods | [../../_Info.txt](../../_Info.txt) |
| `HISTORICAL_ORIGINAL_JAR.txt` | 2026-07-21 (vor v0.1.0) | Jar-Listing des Original-TiredGirl4-Mods | (kein Nachfolger — historisches Artefakt) |
| `HISTORICAL_SEMANTIC_DIFF.md` | 2026-07-21 (vor v0.1.0) | TiredGirl4 → SyxEconomyMod Semantic-Diff | [../GLOSSARY.md](../GLOSSARY.md) |

---

## Warum eingefroren?

Diese Dateien beschreiben Zustände **vor** der Truth-Konsolidierung am 2026-07-23. Sie
enthalten gezielt veraltete Zahlen und Beschreibungen (3-stufige TreasuryCrisis,
108 Java-Klassen, v1.7.3-Versionierung), die jetzt **nicht mehr** der Repo-Wahrheit entsprechen.
Ihr historischer Wert bleibt — als Nachweis was sich geändert hat — aber sie sind nicht
Teil des aktiven Wissens.

Wenn du eine dieser Dateien öffnest, gehst du bewusst in die Geschichte. Aktuelle
Wahrheit findest du in:

| Was du suchst | Wo es steht |
|---------------|-------------|
| Aktuelle Architektur | [../ARCHITECTURE.md](../ARCHITECTURE.md) |
| Klassen-Glossar (aktuell) | [../GLOSSARY.md](../GLOSSARY.md) |
| Release-Historie | [../../CHANGELOG.md](../../CHANGELOG.md) |
| Audit-Report | [../reports/TRUTH_REPORT.md](../reports/TRUTH_REPORT.md) |
| Build-Konventionen | [../../tools/build-gate.sh](../../tools/build-gate.sh) |

---

## Konvention für neue Archive-Einträge

Neue historische Snapshots (z. B. von großen Refactorings) werden hier abgelegt, wenn:

1. Sie **vor** dem aktuellen Stand eingefroren wurden
2. Sie **nicht** mehr redaktionell gepflegt werden
3. Sie **nicht** in der aktuellen Architektur oder im aktuellen Glossar referenziert werden
4. Sie einen ⚠️ **HISTORISCH — ARCHIVIERT** Banner am Anfang haben (mit Datum + Verweis auf Nachfolger)

Der Banner-Template:

```markdown
> ⚠️ **HISTORISCH — ARCHIVIERT** — Stand: <DATUM> (<PHASE>).
> Dieses Dokument ist ein eingefrorener Snapshot zum damaligen Vergleichszeitpunkt und
> wird nicht mehr gepflegt. Für aktuelle Aussagen siehe:
> - Architektur: [../ARCHITECTURE.md](../ARCHITECTURE.md)
> - Klassen-Übersicht: [../GLOSSARY.md](../GLOSSARY.md)
> - Release-Historie: [../../CHANGELOG.md](../../CHANGELOG.md)
```