package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.main.furnisher.FurnisherStat;
import snake2d.util.sets.LIST;

/**
 * Überprüft die Einrichtungs-Erfüllung (Furnishing Fulfillment) der Siedlung.
 *
 * <p>Verwendet präzise {@link FurnisherStat#get(RoomInstance)} über alle
 * gebauten Räume in {@code SETT.ROOMS()} zur Errechnung des durchschnittlichen
 * Einrichtungsgrades. Fällt sanft auf Holzmangel-Erkennung zurück, wenn keine
 * Räume vorhanden sind.</p>
 */
public final class FurnishingAutomation {

    /** Holz-Bestand unter diesem Wert gilt als kritisch niedrig. */
    private static final double STOCK_CRISIS = 1.0;
    /** Durchschnittlicher Einrichtungsgrad unter 40% gilt als Krise. */
    private static final double FURNISHING_CRISIS_THRESHOLD = 0.40;

    /**
     * Prüft, ob aktuell eine Einrichtungs-Krise besteht.
     *
     * @param snap Aktueller EconSnapshot mit Ressourcen-Arrays
     * @return true wenn Einrichtungskrise besteht
     */
    static boolean detectCrisis(EconSnapshot snap) {
        if (snap == null) return false;

        // Echte API-Prüfung über SETT.ROOMS() und FurnisherStat.get(room)
        if (SETT.ROOMS() != null) {
            double totalFurnishing = 0.0;
            int roomCount = 0;

            try {
                LIST insBlueprints = SETT.ROOMS().ins();
                if (insBlueprints != null) {
                    for (int b = 0; b < insBlueprints.size(); b++) {
                        Object bpObj = insBlueprints.get(b);
                        if (bpObj instanceof RoomBlueprintIns bp && bp.constructor() != null) {
                            for (Object obj : bp.constructor().stats()) {
                                if (obj instanceof FurnisherStat stat) {
                                    for (int i = 0; i < bp.instancesSize(); i++) {
                                        RoomInstance r = bp.getInstance(i);
                                        if (r != null && r.exists()) {
                                            double val = stat.get(r);
                                            if (val >= 0.0) {
                                                totalFurnishing += val;
                                                roomCount++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (RuntimeException t) {
                EventLog.log("FURNISHING", "FurnishingAutomation.detectCrisis() room scan failed: "
                        + t.getClass().getSimpleName() + " — fallback auf Holzkrise-Erkennung.");
            }

            if (roomCount > 0) {
                double avgFurnishing = totalFurnishing / roomCount;
                if (avgFurnishing < FURNISHING_CRISIS_THRESHOLD) {
                    return true;
                }
            }
        }

        // Fallback: Holzkrise-Erkennung
        RESOURCE wood = RESOURCES.WOOD();
        if (wood == null) return false;

        int woodIdx = wood.index();
        if (woodIdx < 0 || woodIdx >= snap.stock.length) return false;

        double woodStock = snap.stock[woodIdx];
        double woodDemand = snap.demandPerDay[woodIdx];
        double woodSupply = snap.supplyPerDay[woodIdx];

        return woodStock <= STOCK_CRISIS
            && woodDemand > 0.0
            && woodSupply < woodDemand;
    }

    private FurnishingAutomation() {}
}
