# God-Class-Guard — Failure Recovery

Wenn `mvn verify install -DskipTests` mit einem **God-Class-Guard-Blocker** scheitert, kannst du folgende Diagnose-Schritte ausführen.

## Schritt 1: Welche Datei ist es?

```
bash tools/god-class-guard.sh --mode=hard
```

Output listet jeden Verstoß mit Pfad + Metriken + Grund. Beispiel:

```
  BLOCK src/vannon/syx/economy/core/SomeEngine.java
       loc=1023 > block 800
       → SomeEngine was split in M-1 but has drifted back
```

## Schritt 2: Drei Fix-Pfade

### Pfad A — Methode/Felder aus der Klasse rausziehen (empfohlen)

1. Identifiziere Sub-Concern in der Klasse (z.B. "CrownTitleHandling" in `WarehouseMarket`)
2. Erstelle neue Klasse in Sub-Package `core/warehouse/market/<SubConcern>.java`
3. Verschiebe die state-tragenden Felder `private → package-private` und übergib sie via Constructor-Injection
4. Schreibe delegierende Wrapper-Methoden in `WarehouseMarket` (z.B. `crownTitle.buyCheaper(roster, wallets)`)
5. Update `EconomySim` und alle Caller der Original-Methoden
6. Verify: `bash tools/god-class-guard.sh --mode=hard` meldet PASS für diese Datei

### Pfad B — Constants-Dump im YAML grandfatheren (nur wenn Pfad A nicht geht)

Falls die Datei strukturell riesig ist (jeden Konstanten-Typ enthält, kein logischer Sub-Concern), kann sie als "grandfathered" eingetragen werden. **NUR** für Constants-Dump-Heuristik (`fields>=50, pubM==0`) oder explizit begründete Adapter-Klassen.

In `tools/god-class-baselines.yml`:

```yaml
legacy_baselines:
  'src/vannon/syx/economy/core/<File>.java':
    loc: <Aktuell>
    pubM: <Aktuell>
    fields: <Aktuell>
    imports: <Aktuell>
    rationale: '<Kurz warum grandfathered>'
```

**Niemand** darf ein God-File grandfatheren ohne explizite Begründung im Sprint-Commit.

### Pfad C — Split-Pattern von Definitionsklasse zu Domain-Engine (Hybrid-Facade)

Wenn die Klasse Interface-Stellen hat (für mehrere Adapter-Varianten, z.B. `Vanilla*/Fallback*`), nutze den Hybrid-Facade-Pattern aus M-1 RFC.

## Schritt 3: Erneut validieren

```bash
mvn verify install -DskipTests -Dskip.bump=true
bash tools/god-class-guard.sh --strict
```

Beide müssen grün sein, bevor der atomic commit erstellt wird.

## Verwandte Tools

- `tools/god-class-guard/emit_yaml.py --dry` — zeige generierte YAML-Vorschau
- `tools/god-class-guard.sh --run-meta-tests` — prüfe dass das Tool selbst funktioniert
- `bash tools/god-class-guard.sh --json` — JSON-Output für CI-Parsing
