# Persistence-Optionen für SyxEconomyMod

> **Wann braucht ein Java-Mod persistente Speicherung außerhalb des Spiel-Saves?**
>
> Version: v0.1.2 | Spiel: Songs of Syx V71.44 | Java 21

Diese Datei erklärt, welche Daten der Mod heute speichert, welche zusätzliche Persistenz nützlich wäre, und welche Technologien für Java 21 in Frage komhen.

---

## 1. Was heute bereits persistiert wird

Der Mod nutzt Songs-of-Syxs eigenes Chunked-Save-System (`Saveable`, `ChunkedSave`) für alles, was direkt zum Spielzustand gehört:

- Bürger-Wallets (`Wallets.java`)
- Firmen-Zustand (`FirmLedger.java`)
- Immobilienbesitz (`PropertyLedger.java`)
- Wirtschaftsstufe (`EconProgression.java`)
- Lager-/Markt-Zustände (`WarehouseMarket`, `StateWarehouses`)

**Für Langzeit-Analyse und Balancing gibt es zusätzlich:**
- `DiagnosticExporter` schreibt 3 CSV-Dateien pro Spieltag.
- Die CSVs liegen in `~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/`.
- `tools/rebalance_plots.py` / `tools/rebalance_dashboard.ipynb` lesen diese CSVs und zeichnen Plots.

---

## 2. Warum SQLite WAL nicht die erste Wahl ist

Die initiale Idee war SQLite mit WAL (Write-Ahead Logging), weil es leichtgewichtig und bekannt ist. Für diesen Java-Mod ist es aber nicht optimal:

| Aspekt | SQLite (WAL) | Bewertung |
|--------|--------------|-----------|
| **Plattform** | Erfordert native `.so`/`.dll` via JDBC | Mehr Deployment-Komplexität |
| **Mod-Ordner** | SQLite-Datei im Mod-Ordner muss von SoSy geladen werden | Risiko, dass die Engine Datei-Locks hält |
| **Wartung** | Schema-Migrationen nötig | Overhead für ein kleines Team |
| **Backup** | `.db`- und `.wal`-Dateien | Spieler können sie löschen/vergessen |
| **Vorteil** | SQL-Queries, Indizes, WAL ist schnell | Nur relevant, wenn wir echte SQL-Analyse brauchen |

Für den aktuellen Anwendungsfall — Append-Only-Zeitreihen aus dem DiagnosticExporter — ist SQLite oft Overkill.

---

## 3. Alternativen im Java-Ökosystem

### A. sqlite-jdbc (wenn SQL wirklich nötig ist)

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.46.0.0</version>
</dependency>
```

**Wann sinnvoll:**
- Komplexe Abfragen über viele Spielsaves hinweg
- Indizes auf Zeitstempel, Ressourcen, Firmen
- Externe Tools sollen direkt auf die DB zugreifen

**Wann nicht:**
- Die Mod muss ohne zusätzliche native Bibliothek auskommen
- Spieler sollen Dateien einfach löschen/verschieben können

---

### B. H2 Database (empfohlen für eingebettete Java-Persistenz)

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
</dependency>
```

**Vorteile:**
- Pure Java, keine nativen Abhängigkeiten
- Datei-basiert (`jdbc:h2:file:...`)
- Kompatibel mit standard SQL
- Kleinere Deployment-Größe als sqlite-jdbc + native Libs

**Nachteile:**
- Zusätzliche JAR-Größe (~2 MB)
- Schema-Migrationen nötig

---

### C. Apache Derby

- Auch pure Java, eingebettet
- Weniger verbreitet als H2
- Größere Footprint als H2

**Nur wenn H2 aus Lizenz-/Policy-Gründen nicht gewollt ist.**

---

### D. Parquet-Dateien (für Zeitreihen-Analyse)

```xml
<dependency>
    <groupId>org.apache.parquet</groupId>
    <artifactId>parquet-hadoop</artifactId>
    <version>1.13.1</version>
</dependency>
```

**Vorteile:**
- Spalten-basiert, kompakt
- Perfekt für Append-Only-CSV-Ersatz
- Python (pandas/pyarrow) kann direkt lesen

**Nachteile:**
- Hadoop-Ökosystem als Dependency
- Für ein kleines Projekt überdimensioniert

---

### E. Rotierte CSV-Dateien (empfohlen als Zwischenschritt)

**Lösung ohne neue Dependency:**
- `DiagnosticExporter` schreibt weiterhin CSV
- Ein neuer `DiagnosticRotator` löscht/lagert Dateien nach X Tagen
- Dateinamen mit Epoch-Timestamp oder Ingame-Tag versehen

**Vorteile:**
- Keine Dependency
- Menschlich lesbar
- Python-Toolchain funktioniert bereits

**Nachteile:**
- Keine SQL-Queries
- Viele kleine Dateien
- Langsamer bei sehr großen Datensätze

---

## 4. Empfehlung

### Kurzfristig (v0.1.x)

**Rotierte CSVs + Python-Toolchain weiter ausbauen.**

- `DiagnosticExporter` um `maxCsvAgeDays` erweitern
- Tägliche CSVs im Unterordner `diagnostics/<save-name>/` speichern
- `rebalance_plots.py` um automatische Archivierung erweitern

### Mittelfristig (v0.2.x)

**H2-Datenbank optional integrieren, wenn SQL-Abfragen nötig werden.**

- `EconHistoryDb` Klasse: Öffnet/Schließt H2-Datei im Mod-Ordner
- Tabelle `daily_macro`, `daily_resources`, `daily_firms`
- Schema-Versionierung via Flyway oder einfache manuelle Migration

### Langfristig (v0.3.x)

**Parquet als Archiv-Format, wenn Datenmenge wächst.**

- Live-Daten in H2
- Archiv in Parquet
- Python-Notebook kann sowohl H2 (über JayDeBeApi) als auch Parquet lesen

---

## 5. Entscheidungsbaum

```
Brauchen wir SQL-Abfragen ingame oder im Tool?
├── Ja, komplexe Queries → H2 Database (eingebettet)
├── Ja, aber nur extern in Python → CSV weiter nutzen
└── Nein, nur Zeitreihen-Append → Rotierte CSVs

Muss der Spieler Dateien einfach löschen können?
├── Ja → CSV oder H2-File (beides löschbar)
└── Nein → H2 oder SQLite

Wollen wir native Libraries vermeiden?
├── Ja → H2
└── Nein → sqlite-jdbc
```

---

## 6. Risiken und Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|--------|---------------|
| Spieler löscht DB/CSV | Wiederherstellen aus Save-Dateien nicht möglich, aber optionaler Export als Backup |
| File-Lock während Save | H2 im Embedded-Modus schließt vor Songs-of-Syx-Save und öffnet danach |
| Schema-Drift | Versionierte Migrationen, `schema_version` Tabelle |
| JAR-Größe | Optionaler Scope, H2 nur shade wenn nötig |

---

## 7. Offene Fragen

1. **Soll die Persistenz Pflicht sein oder optional?** (z. B. nur wenn `EconConfig.diagnosticsExportEnabled = true`)
2. **Wer liest die Daten?** Nur Python-Toolchain oder auch ingame UI?
3. **Wie lange sollen Daten aufbewahrt werden?** Letzte 30 Tage? Letztes Save? Für immer?
4. **Sollen Save-übergreifende Analysen möglich sein?** Dann brauchen wir einen globalen Speicherort außerhalb des Save-Ordners.

---

## Siehe auch

- `src/vannon/syx/economy/core/DiagnosticExporter.java`
- `tools/rebalance_plots.py`
- `tools/rebalance_dashboard.ipynb`
- `docs/ARCHITECTURE.md`
