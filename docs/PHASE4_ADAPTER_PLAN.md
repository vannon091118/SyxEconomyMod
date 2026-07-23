# Phase 4 Adapter-Layer — Bauplan ✅ ABGESCHLOSSEN

> **Version:** v0.1.0 | **Stand:** 2026-07-23 | **Spiel:** V71.44 | **Status:** ✅ Alle 6 Schritte implementiert

## Ziel — erreicht ✅

Alle 5 Stellen im Mod, die nicht compiler-geprüft auf Vanilla-Klassen zugreifen (Reflection, String-Matching), sind jetzt hinter **Adapter-Interfaces** gekapselt. Bei einem Spiel-Update müssen nur die Adapter-Implementierungen geprüft werden — nicht mehr 5 Dateien quer durch den Code.

---

## 1. Die 5 Risiko-Stellen (IST → GELÖST)

| # | Datei | Mechanismus | Vanilla-Ziel | Lösung |
|---|---|---|---|---|
| 1 | `EngineSeams.java` | 6× `getSimpleName().equals()` | 6 AI-Plan-Klassen (package-private) | ✅ `ISyxAI` + `VanillaAIAdapter` — `Class.forName(name, true, GAME_CL)` |
| 2 | `DebtDiplomacyBuffer.java` | 5× `getDeclaredField()` | `DipWarPlayer` private Felder | ✅ `ISyxDiplomacy` + `VanillaDiplomacyAdapter`/`VanillaDiplomacyAdapterMH` + `FallbackDiplomacyAdapter` |
| 3 | `TransportMarket.java` | `getDeclaredField("distance")` | `TransportInstance` float | ✅ `ISyxTransport` + `VanillaTransportAdapter`/`VanillaTransportAdapterMH` + `FallbackTransportAdapter` |
| 4 | `StateWarehouses.java` | `getDeclaredMethod("storingSet")` | `StockpileInstance` Methode | ✅ `ISyxWarehouse` + `VanillaWarehouseAdapter`/`VanillaWarehouseAdapterMH` + `FallbackWarehouseAdapter` |
| 5 | `EconProgression.java` | `getDeclaredFields()` Iteration | `BOOSTABLES.CIVICS().GOV` | ✅ `ISyxBoosting` + `VanillaBoostingAdapter` + `FallbackBoostingAdapter` |

---

## 2. Interface-Design (5 Adapter — alle implementiert ✅)

### 2.1 `ISyxAI` — AI-Plan-Erkennung
```java
public interface ISyxAI {
    boolean isOddjobbing(Humanoid humanoid);
    boolean isFoodPlan(AIPLAN plan);
    boolean isTavernPlan(AIPLAN plan);
    boolean isMarketPlan(AIPLAN plan);
}
```

### 2.2 `ISyxTransport` — Transport-Distanz
```java
public interface ISyxTransport {
    boolean isDistanceAvailable();
    double getReflectedDistance(RoomInstance station);
    double getGeometricDistance(RoomInstance station, ROOM_STATION unloading);
}
```

### 2.3 `ISyxWarehouse` — Staatslager-Sperre
```java
public interface ISyxWarehouse {
    boolean isStoringLockAvailable();
    void setStoring(StockpileInstance granary, boolean locked);
}
```

### 2.4 `ISyxBoosting` — Admin/GOV-Booster
```java
public interface ISyxBoosting {
    boolean isAdminBoosterAvailable();
    void registerAdminBooster();
    Boostable getAdminBoostable();
}
```

### 2.5 `ISyxDiplomacy` — Diplomatie-Puffer
```java
public interface ISyxDiplomacy {
    boolean isAvailable();
    void setUpdateIndex(int index);
    void setPlayerPower(double power);
    void setCoalitionPower(double power);
    Bitmap1D getWillingBits();
    ArrayList<FactionNPC> getWillingList();
    DipWarPlayer getWarPlayer();
}
```

---

## 3. Implementierungen — alle 12 erstellt ✅

| Interface | VanillaAdapter | FallbackAdapter | MH-optimiert |
|---|---|---|---|
| `ISyxAI` | `VanillaAIAdapter` | — (try/catch reicht) | — |
| `ISyxTransport` | `VanillaTransportAdapter` | `FallbackTransportAdapter` | `VanillaTransportAdapterMH` |
| `ISyxWarehouse` | `VanillaWarehouseAdapter` | `FallbackWarehouseAdapter` | `VanillaWarehouseAdapterMH` |
| `ISyxBoosting` | `VanillaBoostingAdapter` | `FallbackBoostingAdapter` | — |
| `ISyxDiplomacy` | `VanillaDiplomacyAdapter` | `FallbackDiplomacyAdapter` | `VanillaDiplomacyAdapterMH` |

### MethodHandle-Optimierung (3 Adapter)

- **`VanillaTransportAdapterMH`**: `Field.getFloat()` → `VarHandle.get()`
- **`VanillaDiplomacyAdapterMH`**: 5× `Field.get/set` → 5× `VarHandle`
- **`VanillaWarehouseAdapterMH`**: `Method.invoke()` → `MethodHandle.invokeExact()`
- Toggle: `EconConfig.useMethodHandleAdapters = false` (default)
- Benchmark: `AdapterReflectionBenchmark.java`

---

## 4. Datei-Struktur (IST ✅)

```
src/vannon/syx/economy/
├── core/                          ← 90 Dateien (unverändert, delegieren an Adapter)
│   ├── EconomySim.java            ← hält 5 Adapter-Instanzen
│   ├── DebtDiplomacyBuffer.java   ← Business-Logik via ISyxDiplomacy
│   ├── TransportMarket.java       ← Business-Logik via ISyxTransport
│   ├── StateWarehouses.java       ← Business-Logik via ISyxWarehouse
│   ├── EconProgression.java       ← Business-Logik via ISyxBoosting
│   └── EngineSeams.java           ← Business-Logik via ISyxAI
├── adapter/                       ← 17 Dateien (fertig)
│   ├── ISyxAI.java
│   ├── ISyxTransport.java
│   ├── ISyxWarehouse.java
│   ├── ISyxBoosting.java
│   ├── ISyxDiplomacy.java
│   ├── VanillaAIAdapter.java
│   ├── VanillaTransportAdapter.java
│   ├── VanillaTransportAdapterMH.java
│   ├── VanillaWarehouseAdapter.java
│   ├── VanillaWarehouseAdapterMH.java
│   ├── VanillaBoostingAdapter.java
│   ├── VanillaDiplomacyAdapter.java
│   ├── VanillaDiplomacyAdapterMH.java
│   ├── FallbackTransportAdapter.java
│   ├── FallbackWarehouseAdapter.java
│   ├── FallbackBoostingAdapter.java
│   └── FallbackDiplomacyAdapter.java
└── benchmark/
    └── AdapterReflectionBenchmark.java
```

---

## 5. Migrations-Schritte — alle erledigt ✅

### Schritt 5.1: ISyxAI + VanillaAIAdapter ✅
- `ISyxAI.java` + `VanillaAIAdapter.java` erstellt
- 6 String-Konstanten + 4 try/catch-Methoden aus EngineSeams extrahiert
- `VanillaAIAdapter` als Instanz in `EconomySim`
- Alle Aufrufer auf `sim.aiAdapter().isXxx()` umgestellt
- Alte `@Deprecated` Methoden aus `EngineSeams` gelöscht
- **ClassLoader-Fix**: `Class.forName(name)` → `Class.forName(name, true, GAME_CL)`

### Schritt 5.2: ISyxTransport + VanillaTransportAdapter ✅
- `ISyxTransport.java` + `VanillaTransportAdapter.java` + `FallbackTransportAdapter.java` + `VanillaTransportAdapterMH.java` erstellt
- `VanillaTransportAdapter` extrahiert `reflectDistance()`-Logik aus `TransportMarket`
- `TransportMarket` bekommt `ISyxTransport` als Constructor-Parameter
- **ClassLoader-Fix** ebenso angewendet

### Schritt 5.3: ISyxWarehouse + VanillaWarehouseAdapter ✅
- `ISyxWarehouse.java` + `VanillaWarehouseAdapter.java` + `FallbackWarehouseAdapter.java` + `VanillaWarehouseAdapterMH.java` erstellt
- `StateWarehouses` bekommt `ISyxWarehouse` via Constructor
- `storingSet` → `hasStoringLock` umbenannt

### Schritt 5.4: ISyxBoosting + VanillaBoostingAdapter ✅
- `ISyxBoosting.java` + `VanillaBoostingAdapter.java` + `FallbackBoostingAdapter.java` erstellt
- `EconProgression.registerAdminBooster()` → `boostingAdapter.registerAdminBooster()`
- `Field.getDouble` statt Boxing-Dance

### Schritt 5.5: ISyxDiplomacy + VanillaDiplomacyAdapter ✅
- `ISyxDiplomacy.java` + `VanillaDiplomacyAdapter.java` + `FallbackDiplomacyAdapter.java` + `VanillaDiplomacyAdapterMH.java` erstellt
- 5× `DipWarPlayer`-Felder über Adapter gekapselt
- `DebtDiplomacyBuffer` wurde zu reiner Business-Logik
- Alle 5 Felder via `unzip -p` gegen `SongsOfSyx-sources.jar` verifiziert

### Schritt 5.6: Cleanup ✅
- Alte `@Deprecated` Methoden aus `EngineSeams` gelöscht
- `EngineSeams.isFoodPlan()`/`isOddjobbing()`-Aufrufer auf `economySim`-Adapter umgestellt
- Alle direkten Reflection-Stellen eliminiert
- 0 Reflection-Stellen außerhalb `adapter/`-Package

---

## 6. Zusätzliche Arbeiten (über Plan hinaus)

| Arbeit | Status |
|--------|--------|
| ClassLoader-Fix (3 Adapter) | ✅ |
| MethodHandle-Optimierung (3 Adapter) | ✅ |
| Reflection-Benchmark | ✅ |
| DiagnosticExporter (3 CSVs) | ✅ |
| TreasuryCrisis | ✅ |
| Debug-Tab | ✅ |
| Build-Gate-System | ✅ |
| Catch-Tightening (10 Sites) | ✅ |
| Stage-gated Wallet | ✅ |
| Rebalance-Dashboard (Python) | ✅ |
| Hard-Threshold-Alert-Layer | ✅ |
| pom.xml-Metadaten-Sync | ✅ |

---

## 7. Test-Strategie — erfüllt ✅

| Test | Ergebnis |
|---|---|
| Kompilierung | `mvn clean compile` → BUILD SUCCESS |
| Build-Gates | Bestanden: 3, Fehlgeschlagen: 0, Übersprungen: 0 |
| Code-Audit | 0 Blocker, 0 Warnungen |
| Keine SEAM-Einträge | Alle Adapter via ClassLoader-Fix funktional |

---

## 8. Risiko-Abwägung

| Risiko | Mitigation | Status |
|---|---|---|
| Reflection-Initialisierung schlägt im Adapter-Konstruktor fehl | FallbackAdapter wird stattdessen injiziert | ✅ |
| Typsystem-Änderung im Spiel | Nur 5 Adapter-Dateien müssen geprüft werden | ✅ |
| Interface-Änderung bricht alle Implementierungen | Interfaces sind minimal (4–7 Methoden) | ✅ |
| EventLog-Spam beim Umschalten Vanilla→Fallback | One-Shot-Guard im VanillaAdapter | ✅ |
