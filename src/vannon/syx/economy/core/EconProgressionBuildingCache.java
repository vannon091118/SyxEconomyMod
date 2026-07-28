package vannon.syx.economy.core;

import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import snake2d.util.sets.LIST;

/**
 * Building-type cache for {@link EconProgression}.
 * Extracted to keep EconProgression under the God-Class-Guard thresholds.
 * Package-private — internal detail of the progression system.
 */
final class EconProgressionBuildingCache {

    int cachedStockpileCount  = 0;
    int cachedTavernCount     = 0;
    int cachedMarketCount     = 0;
    int cachedTempleCount     = 0;
    int cachedExportCount     = 0;
    int cachedLabCount        = 0;
    int cachedLibraryCount    = 0;
    int cachedMilitaryCount   = 0;
    int cachedEmbassyCount    = 0;

    /**
     * Counts building instances of each type via SETT.ROOMS().imps().
     * Stockpile is counted via instanceof loop (not STOCKPILE.instancesSize())
     * because Live-Test showed a discrepancy.
     */
    void pollBuildings() {
        if (SETT.ROOMS() == null) return;

        cachedStockpileCount = cachedTavernCount = cachedMarketCount = cachedTempleCount = 0;
        cachedExportCount = cachedLabCount = cachedLibraryCount = 0;
        cachedMilitaryCount = cachedEmbassyCount = 0;

        LIST<RoomBlueprintImp> all = SETT.ROOMS().imps();
        for (int i = 0; i < all.size(); i++) {
            RoomBlueprintImp b = all.get(i);
            if (!(b instanceof RoomBlueprintIns)) continue;
            int count = ((RoomBlueprintIns) b).instancesSize();
            if (count <= 0) continue;

            if (b instanceof settlement.room.infra.stockpile.ROOM_STOCKPILE)   cachedStockpileCount += count;
            if (b instanceof settlement.room.service.food.tavern.ROOM_TAVERN)  cachedTavernCount   += count;
            if (b instanceof settlement.room.service.market.ROOM_MARKET)       cachedMarketCount   += count;
            if (b instanceof settlement.room.spirit.temple.ROOM_TEMPLE)        cachedTempleCount   += count;
            if (b instanceof settlement.room.infra.export.ROOM_EXPORT)         cachedExportCount   += count;
            if (b instanceof settlement.room.knowledge.laboratory.ROOM_LABORATORY) cachedLabCount     += count;
            if (b instanceof settlement.room.knowledge.library.ROOM_LIBRARY)   cachedLibraryCount  += count;
            if (b instanceof settlement.room.military.training.ROOM_M_TRAINER) cachedMilitaryCount += count;
            if (b instanceof settlement.room.infra.embassy.ROOM_EMBASSY)       cachedEmbassyCount  += count;
        }
    }

    /**
     * Reliabeler Stockpile-Count via SETT.ROOMS().imps() statt STOCKPILE.instancesSize().
     * Hintergrund: Live-Test zeigte Diskrepanz.
     * @return Anzahl der Stockpile-Instanzen oder 0 wenn keine gefunden.
     */
    static int reliableStockpileCount() {
        if (SETT.ROOMS() == null) return 0;
        LIST<RoomBlueprintImp> all = SETT.ROOMS().imps();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) instanceof settlement.room.infra.stockpile.ROOM_STOCKPILE) {
                return ((RoomBlueprintIns) all.get(i)).instancesSize();
            }
        }
        return 0;
    }

    /** Reset all cached counts to zero. */
    void clear() {
        cachedStockpileCount = 0;
        cachedTavernCount = 0;
        cachedMarketCount = 0;
        cachedTempleCount = 0;
        cachedExportCount = 0;
        cachedLabCount = 0;
        cachedLibraryCount = 0;
        cachedMilitaryCount = 0;
        cachedEmbassyCount = 0;
    }
}
