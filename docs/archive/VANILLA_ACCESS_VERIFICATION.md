# Vanilla Access Verification — SyxEconomyMod v1.7.2

> **Datum:** 2026-07-23 | **Quelle:** SongsOfSyx-sources.jar (V71.44) | **Methode:** `unzip -p` gegen dekompilierten Vanilla-Source

---

## Zusammenfassung

Fünf Behauptungen aus der GUI-vs-Mod-Gap-Analyse wurden gegen den echten Vanilla-Sourcecode geprüft:

| # | Claim | Verdict |
|---|---|---|
| 1 | Tätigkeit 1.5% — RoomStats Vanilla-intern | ✅ **CONFIRMED** |
| 2 | Einrichtung 41% — FurnitureModule Vanilla-intern | ⚠️ **PARTIALLY FALSIFIED** |
| 3 | Loyalty/Happiness % — keine öffentlichen Getter | ⚠️ **PARTIALLY FALSIFIED** |
| 4 | Ausrüstungs-Slots — STATS-intern | ⚠️ **PARTIALLY CONFIRMED** |
| 5 | Physik/Verhalten/Traits — HumanoidEntity-intern | ✅ **CONFIRMED** |

---

## Claim 1: Tätigkeit (Activity) — RoomStats

**Behauptung:** "Tätigkeit 0.176/12.000 — `RoomStats` ist Vanilla-intern"

**Quelle:** `settlement/room/main/util/RoomStats.java`

**Öffentliche Methoden:**
```java
public RoomStatsList finished()
public RoomStatsList broken()
public void add(int mx, int my)
public void remove(int mx, int my)
public int amount()
public COORDINATE poll()
public void save(FilePutter file)
public void load(FileGetter file)
public void clear()
```

**Befund:** RoomStats ist ein Koordinaten-/Zustands-Tracker für Räume, KEIN Fulfillment-Aggregator. Es gibt keine öffentlichen Getter für "Tätigkeit", "Zugang", "Dienstleistungen", "Umwelt", "Religion" oder "Regierung". Die im GUI sichtbaren Werte (0.176/12.000) kommen aus einer anderen Quelle — vermutlich aus `STATS`-Subkategorien oder einer UI-eigenen Aggregation.

**Verdict: ✅ CONFIRMED** — RoomStats ist nicht die Quelle für Fulfillment-Daten. Tätigkeit ist über diese Klasse nicht lesbar.

---

## Claim 2: Einrichtung (Furnishing) — FurnitureModule

**Behauptung:** "Einrichtung 41% — `FurnitureModule` ist Vanilla-intern"

**Quelle:** Kein `FurnitureModule.java` in sources.jar. Die tatsächliche Klasse heißt:

```
settlement/room/main/furnisher/Furnisher.java
settlement/room/main/furnisher/FurnisherStat.java
settlement/room/main/furnisher/FurnisherItem.java
settlement/room/main/furnisher/FurnisherItemTile.java
```

**Öffentliche Methoden (Furnisher.java):**
```java
public final int resources()
public abstract boolean usesArea()
// groups() / pgroups() → LIST<FurnisherItemGroup>
```

**Öffentliche Methoden (FurnisherStat.java):**
```java
public double get(AREA area, double[] fromItems)
public abstract double get(AREA area, double acc)
public double get(RoomInstance r)      // ← KÖNNTE Fulfillment zurückgeben
public final double min
```

**Befund:** `FurnitureModule` existiert nicht — der Klassenname war falsch. Die tatsächliche Klasse `Furnisher` hat öffentliche Methoden, und `FurnisherStat.get(RoomInstance r)` gibt einen `double` zurück, der die Einrichtungs-Erfüllung sein KÖNNTE. **Ohne Runtime-Test nicht definitiv verifizierbar**, aber die API ist öffentlich zugänglich.

**Verdict: ⚠️ PARTIALLY FALSIFIED** — Der Name war falsch (FurnitureModule ≠ Furnisher), und es GIBT öffentliche Getter (`FurnisherStat.get(RoomInstance r)`). Ob der Rückgabewert tatsächlich "Einrichtung %" ist, muss ein Runtime-Test zeigen.

**→ Action Item:** `FurnisherStat.get(room)` im Mod aufrufen und prüfen, ob der Wert mit dem GUI-Wert "Einrichtung 41%" übereinstimmt.

---

## Claim 3: Loyalty/Happiness % — keine öffentlichen Getter

**Behauptung:** "Loyalty/Happiness % — keine öffentlichen Getter"

**Quelle:** `game/boosting/BOOSTABLES.java` → `Behaviour`-Klasse

**Öffentliche Felder:**
```java
public final Boostable LOYALTY;
public final Boostable HAPPI;
public final Boostable HAPPI_SLAVES;
```

**Öffentliche Methode auf Boostable:**
```java
// game/boosting/Boostable.java
public double get(BOOSTABLE_O t) {
    double res = BUtil.value(all, t, baseValue, 1, minValue);
    ...
}
```

**Befund:** `Boostable.get(BOOSTABLE_O t)` IST ein öffentlicher Getter, der den aktuellen Booster-Wert für ein gegebenes Objekt (z. B. einen Induvidual oder eine Faction) zurückgibt. Allerdings:
- Es ist **per-Entity**, nicht aggregiert (kein `getFactionLoyalty()`)
- Es braucht einen `BOOSTABLE_O`-Parameter (Implementierer dieses Interface)
- Ob `FACTIONS.player()` oder `Faction` `BOOSTABLE_O` implementiert, ist nicht geprüft

**Verdict: ⚠️ PARTIALLY FALSIFIED** — Es GIBT einen öffentlichen Getter (`Boostable.get()`), aber er ist per-Entity, nicht als einfacher Aggregatwert. Die Behauptung "keine öffentlichen Getter" ist technisch falsch — der Getter existiert, ist aber umständlich zu nutzen.

**→ Action Item:** Prüfen ob `FACTIONS.player()` `BOOSTABLE_O` implementiert. Falls ja: `BOOSTABLES.BEHAVIOUR().LOYALTY.get(FACTIONS.player())` könnte den aggregierten Loyalty-Wert liefern.

---

## Claim 4: Ausrüstungs-Slots — STATS-intern

**Behauptung:** "Ausrüstungs-Slots — `STATS`-intern"

**Quellen:**
- `init/race/RACES.java`: `public static RaceResources res()`, `public static LIST<Race> all()` ✅ ÖFFENTLICH
- `settlement/stats/Induvidual.java`: Implementiert `BOOSTABLE_O`, aber KEINE `equip`/`wear`/`armor`/`cloth`/`slot`-Methoden

**Befund:** RACES und Race-Definitionen sind vollständig öffentlich. Aber die **individuellen Ausrüstungs-Slots** eines Bürgers (Kleidung 1, Schmuck 0, Lederrüstung 0, etc.) sind NICHT über `Induvidual` öffentlich zugänglich. Der Pfad zu individueller Ausrüstung führt vermutlich über `Humanoid` → `RACES.res().get(Wearable)` oder eine ähnliche Indirektion.

**Verdict: ⚠️ PARTIALLY CONFIRMED** — Race-Definitionen sind öffentlich, aber individuelle Ausrüstungs-Slots sind nicht direkt über `Induvidual` oder `STATS` lesbar.

---

## Claim 5: Physik/Verhalten/Traits — HumanoidEntity-intern

**Behauptung:** "Physik/Verhalten/Traits — `HumanoidEntity`-intern"

**Quelle:** `settlement/entity/humanoid/Humanoid.java`

**Öffentliche Methoden (Auszug):**
```java
public Race race()
public Induvidual indu()
public HAI ai()
public CharSequence title()
public boolean noble()
public void nobleSet(boolean)
public void interrupt()
public void knockOut()
public DIVISION division()
// KEINE trait(), bodies(), stats(), physics(), personality()
```

**Befund:** `Humanoid` hat KEINEN öffentlichen `trait()`-, `bodies()`- oder `stats()`-Getter. Physik-Daten (Gewicht, Geschwindigkeit, Gesundheit) und Persönlichkeits-Traits (Aggression, Stolz, Faulheit) sind NICHT direkt über `Humanoid`-Methoden zugänglich.

Der Zugriffspfad wäre: `Humanoid.race().stats` → `RaceStats` (race-level, nicht individual-level). Oder über `Humanoid.indu()` → `Induvidual` → `BOOSTABLE_O` → Boostable.get() für einzelne Werte.

**Verdict: ✅ CONFIRMED** — Kein direkter öffentlicher Zugriff auf individuelle Physik/Traits über Humanoid. Nur Race-Level-Stats via `race()`.

---

## Action Items (priorisiert nach Machbarkeit)

| Prio | Fund | Nächster Schritt |
|---|---|---|
| 🔴 | `FurnisherStat.get(RoomInstance r)` — öffentlich! | Runtime-Test: Wert abrufen und mit GUI "Einrichtung 41%" vergleichen |
| 🔴 | `Boostable.get(BOOSTABLE_O)` — öffentlich! | Prüfen ob `FACTIONS.player()` BOOSTABLE_O ist → Loyalty/Happiness direkt lesbar |
| 🟡 | Ausrüstungs-Slots | Pfad über `Humanoid.race()` → `RACES.res()` → Wearable-API erforschen |
| 🟢 | Tätigkeit (Activity) | Quelle im GUI identifizieren (nicht RoomStats) — vermutlich STATS-Unterkategorie oder UI-Aggregation |

---

*Geprüft gegen: SongsOfSyx-sources.jar (V71.44), 2026-07-23*
