# SyxEconomyMod — LLM Workflow

> **Obligatorisches 3-Phasen-Muster für jede AI-Agent-Session.**
> Abgeleitet aus der Phase-A–F Session (2026-07-25), in der zwei unabhängige
> Reviewer 11 Lücken ohne Überschneidung fanden — ein dritter Durchgang hätte
> weitere gefunden. Dieses Dokument verhindert, dass Lücken bis zum
> nächsten Reviewer-Durchgang überleben.
>
> Letzte Aktualisierung: 2026-07-25 | Session: Phase-A–F + Baseline + Double-Review

---

## Die 3 Phasen — obligatorisch, in dieser Reihenfolge

Jede AI-Agent-Session, die Code ändert, durchläuft diese drei Phasen.
Keine Phase darf übersprungen werden. Keine Phase darf mit der nächsten
beginnen, bevor die vorherige abgeschlossen und committed ist.

```
PHASE 1: BAUEN       PHASE 2: PRÜFEN        PHASE 3: HÄRTEN
┌──────────────┐    ┌──────────────┐    ┌──────────────────┐
│ Feature bauen│ → │ Gate checken  │ → │ Review einholen   │
│ Stam-Docs    │    │ Stale-Refs    │    │ Lücken schließen │
│ mit committen│    │ Drift fixen   │    │ Kein silent-fail │
│ mvn verify   │    │ Phantome weg  │    │ Handoff-ready    │
└──────────────┘    └──────────────┘    └──────────────────┘
```

---

## Phase 1: BAUEN — Konstruktion mit Commit-Disziplin

### Regeln

| # | Regel | Begründung |
|---|---|---|
| 1.1 | **0 bestehende Dateien brechen.** Neue Dateien oder targeted Rewrites. Kein „ich ändere mal eben 5 Consumer mit". | Die Session hat 17→14 Adapter-Dateien ohne einen einzigen Core-Consumer anzufassen. |
| 1.2 | **Stam-Docs im selben Commit.** Jeder Commit mit neuen/gelöschten/umbenannten .java-Dateien enthält die ARCHITECTURE/GLOSSARY/ROADMAP/CHANGELOG-Updates. | Der Gate feuert bei `mvn install` (validate-Phase), nicht bei compile. |
| 1.3 | **`mvn verify install -DskipTests` nach jedem Commit.** Kein Commit ohne grünen Build. | Siehe agents.md Regel 1. |
| 1.4 | **Atomare Commits pro logischer Einheit.** Nicht „alles in einem Commit". Jede Phase (B/C/D/E/F) war ein eigener Commit. | Rückverfolgbarkeit. |
| 1.5 | **Commit-Message folgt Schema:** `feat:`/`fix:`/`docs:`/`chore:` + Phase + Beschreibung. | Siehe git log: `feat: Phase C — Transport-Adapter auf BypassGate migriert` |

### Checkliste vor Phase-1-Abschluss

- [ ] Alle neuen/gelöschten Dateien in ARCHITECTURE.md nachgetragen
- [ ] GLOSSARY.md: Phantom-Klassen entfernt, neue ergänzt
- [ ] CHANGELOG.md: Eintrag unter aktueller Version
- [ ] ROADMAP.md: Status aktualisiert
- [ ] `mvn verify install -DskipTests` = BUILD SUCCESS
- [ ] Alle Commits gepusht

---

## Phase 2: PRÜFEN — Baseline-Konsistenz

### Regeln

| # | Regel | Begründung |
|---|---|---|
| 2.1 | **Sync-Gate explizit ausführen:** `bash tools/verify-doc-sync.sh`. Nicht nur auf `mvn verify` verlassen — der Gate prüft nur bestimmte Muster. | Der CHANGELOG-Header blieb auf v0.13.2, während der `## v`-Eintrag korrekt war. Der Gate prüfte nur den Eintrag. |
| 2.2 | **Stale-Referenz-Scan:** `grep -rn 'Fallback\|*MH\|useMethodHandleAdapters' src/ test/`. Jeder Treffer muss entweder legitimer Kommentar oder toter Code sein. | Nach Phase F existierten keine Fallback-Klassen mehr, aber die Stam-Docs behaupteten es. |
| 2.3 | **Phantom-Dokumentation löschen.** Jede Behauptung in ARCHITECTURE/GLOSSARY/ROADMAP muss mit `find src/ -name '*.java'` verifizierbar sein. Keine Klasse dokumentieren, die nicht existiert. | agents.md Regel 5: „Don't paraphrase numbers, count them." |
| 2.4 | **Version-Drift aktiv suchen.** `grep -m1 'Version:'` in allen 5 Stam-Docs gegen `pom.xml`. Der Sync-Gate fängt nicht alle Muster. | Regel 3 in agents.md sagt „intentional friction" — der Mensch muss den Drift SEHEN. |
| 2.5 | **Gate-Abdeckung prüfen.** Welche Muster prüft der Gate? Welche übersieht er? Lücken dokumentieren (nicht unbedingt sofort fixen). | Der Gate prüft CHANGELOGs `## v`-Eintrag, aber nicht den `**Version:**`-Header. Das ist dokumentiert. |

### Checkliste vor Phase-2-Abschluss

- [ ] `bash tools/verify-doc-sync.sh` = PASS (alle 7 Dateien)
- [ ] `grep -rn 'Fallback' src/ test/` = nur legitime Kommentare, keine toten Imports
- [ ] `grep -rn 'MH' src/vannon/syx/economy/adapter/` = keine toten MH-Referenzen
- [ ] Alle 5 Stam-Docs: Version-Stamp = `pom.xml <version>`
- [ ] ARCHITECTURE.md Datei-Count = `find src/ -name '*.java' | wc -l`
- [ ] Keine Phantom-Klassen in GLOSSARY.md
- [ ] agents.md: Alle neuen Regeln/Patterns aus der Session ergänzt
- [ ] Commit + Push

---

## Phase 3: HÄRTEN — Review & Gap-Closure

### Regeln

| # | Regel | Begründung |
|---|---|---|
| 3.1 | **Mindestens ein code-reviewer-deepseek nach jeder signifikanten Änderung.** Der Reviewer findet andere Lücken als der bauende Agent. | Zwei Reviewer fanden 11 Lücken mit NULL Überschneidungen. |
| 3.2 | **Alle gefundenen Lücken schließen vor Handoff.** Kein „das machen wir später". Jede Lücke ist entweder gefixt oder explizit als „akzeptiertes Risiko" dokumentiert. | 11 Lücken, 11 Fixes im selben Commit. |
| 3.3 | **Kein silent-fail in irgendeinem Codepfad.** Jeder Fehlerfall produziert entweder eine Exception mit Kontext oder einen EventLog-Eintrag. Niemals `return 0`/`return null`/`return false` ohne Diagnose. | FieldAccessor gab bei Totalausfall 0 zurück — stiller Bilanz-Fehler im Economy-Mod. |
| 3.4 | **Fehlerverhalten konsistent.** Geschwisterklassen (FieldAccessor/MethodAccessor) müssen bei identischer Fehlerbedingung identisch reagieren. | FieldAccessor schwieg (return 0), MethodAccessor crashte (NPE). Beides falsch, aber vor allem: inkonsistent. |
| 3.5 | **Double-Review für architekturkritische Dateien.** Die 4 SDK-Dateien (BypassGate, FieldAccessor, MethodAccessor, ClassResolver) sind das Fundament aller Adapter. Ein Fehler hier propagiert in 5+ Consumer. | Zweiter Reviewer fand den Lookup-Kontext-Bug, der im ersten Durchgang übersehen wurde. |

### Checkliste vor Phase-3-Abschluss

- [ ] `code-reviewer-deepseek` ausgeführt, alle Anmerkungen adressiert
- [ ] Kein `return 0`/`null`/`false` ohne Diagnose in neuem Code
- [ ] Fehlerverhalten zwischen Geschwisterklassen konsistent
- [ ] Architekturkritische Dateien haben Double-Review
- [ ] `mvn verify install -DskipTests` = BUILD SUCCESS
- [ ] Alle Lücken geschlossen oder explizit dokumentiert
- [ ] Commit + Push

---

## Commit-Disziplin — Session-übergreifend

**Commit + Push erfolgt AM ENDE JEDER CODE-ÄNDERUNG, die Review
durchlaufen hat.** Kein Stapeln von reviewed-but-uncommitted Changes.
Kein „ich mach mehrere Fixes und commit dann alles auf einmal".
Jeder Review→Fix→Verify-Zyklus endet mit einem atomaren Commit + Push.

```
JEDER Commit:
  ├── Enthält Stam-Doc-Updates (wenn .java-Dateien geändert)
  ├── Hat `mvn verify install -DskipTests` = BUILD SUCCESS
  ├── Folgt Schema: type: Phase/Ort — Beschreibung
  └── Wird gepusht sobald die aktuelle Änderung reviewed und verified ist
      (kein Stapeln mehrerer ungepushter Commits)

NIE:
  ├── „Docs mache ich später"
  ├── „Review mache ich später"
  ├── „Gate fixe ich später"
  ├── Commit ohne Build
  ├── Review abschließen ohne Commit+Push
  └── Mehrere reviewed Changes in EINEM Commit stapeln
```

---

## Session-Handoff-Kriterien

Eine Session ist bereit zum Handoff, wenn:

1. ✅ Alle 3 Phasen durchlaufen
2. ✅ `mvn verify install -DskipTests` = BUILD SUCCESS
3. ✅ `bash tools/verify-doc-sync.sh` = PASS
4. ✅ `git status` = clean (keine uncommitteten Änderungen)
5. ✅ Keine offenen Review-Lücken
6. ✅ agents.md auf aktuellem Stand (neue Regeln/Patterns dokumentiert)
7. ✅ WORKFLOW.md auf aktuellem Stand (neue Erkenntnisse generalisiert)

---

## Anti-Patterns — erkannt in dieser Session

| Anti-Pattern | Symptom | Folge | Fix |
|---|---|---|---|
| **Doku-Phantom** | GLOSSARY listet 4 Fallback-Klassen, die gelöscht wurden | Agent erzeugt neue Fallback-Klasse „weil's in der Doku steht" | Phase 2.3: Phantom-Scan |
| **Gate-Blindspot** | Sync-Gate passt, aber CHANGELOG-Header ist 8 Versionen alt | Agent liest v0.13.2 als Kontext, baut gegen falsche Version | Phase 2.1+2.5: Manueller Gate-Check |
| **Silent-Zero** | FieldAccessor.get() gibt 0 zurück bei Totalausfall | Wirtschaftssimulation rechnet mit Phantom-Nullen | Phase 3.3: Exception statt silent-fail |
| **Inkonsistente Geschwister** | FieldAccessor schweigt, MethodAccessor crasht | Selber Fehler, zwei verschiedene Folgen | Phase 3.4: Konsistenz-Prüfung |
| **Doku-Lüge** | „BypassGate.isAvailable() ersetzt Fallbacks" klingt nach globalem Flag | Agent denkt granulare Degradation sei verloren | Phase 2.3: Claims gegen Code verifizieren |
| **Review-ohne-Commit** | code-reviewer-deepseek läuft, Lücken werden gefixt, aber kein Commit+Push | Changes leben nur im Chat, nächster Agent sieht sie nicht | Commit+Push AM ENDE JEDER reviewed Änderung |

---

> **Pflege-Hinweis:** Dieses Dokument lebt. Jede Session, die ein neues Anti-Pattern
> oder eine neue Lücken-Kategorie entdeckt, ergänzt die entsprechenden Checklisten.
> WORKFLOW.md ist der GENERALISIERTE EXTRAKT aus den konkreten Session-Erfahrungen —
> nicht die Erfahrung selbst (die steht in CHANGELOG.md und den Commit-Messages).
