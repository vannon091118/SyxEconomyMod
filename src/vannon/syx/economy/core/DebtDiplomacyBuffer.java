package vannon.syx.economy.core;

import game.GAME;
import game.battle.util.DIV_SPEC;
import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipWarPlayer;
import game.faction.npc.FactionNPC;
import game.faction.player.Player;
import game.faction.royalty.opinion.ROPINION;
import settlement.main.SETT;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.ISyxDiplomacy;
import vannon.syx.economy.core.EconConfig;
import world.army.AD;
import world.army.WDivMercenary;
import world.region.RD;

final class DebtDiplomacyBuffer {
    /** Phase 4.5: alle fünf DipWarPlayer-Reflections sind im Adapter gekapselt. */
    private final ISyxDiplomacy diplomacyAdapter;
    private boolean runtimeErrorLogged;
    private int deterredCount = 0;
    private int lastLoggedCount = -1;
    private int lastLoggedSeason = -1;

    /** Wie viele Fraktionen aktuell durch den Schulden-Puffer abgeschreckt werden. */
    public int deterredCount() {
        return !diplomacyAdapter.isAvailable() || !EconConfig.diplomacyDebtBufferEnabled ? 0 : deterredCount;
    }

    /** Ob der Puffer aktiv ist (Reflection verfügbar + Config an). */
    public boolean isActive() { return diplomacyAdapter.isAvailable() && EconConfig.diplomacyDebtBufferEnabled; }

    DebtDiplomacyBuffer(ISyxDiplomacy diplomacyAdapter) {
        this.diplomacyAdapter = diplomacyAdapter;
    }

    void update() {
        block17: {
            if (!this.diplomacyAdapter.isAvailable() || !EconConfig.diplomacyDebtBufferEnabled) {
                return;
            }
            if (EconConfig.diplomacyDebtThreshold >= 0L) {
                return;
            }
            try {
                DipWarPlayer war = DIP.WAR_PLAYER();
                if (war == null || FACTIONS.player() == null) {
                    return;
                }
                if (FACTIONS.player().capitolRegion() == null) {
                    return;
                }
                LIST<?> vanillaPotential = war.potential();
                boolean[] potential = new boolean[FACTIONS.MAX()];
                for (Object factionObj : vanillaPotential) {
                    FactionNPC faction = (FactionNPC) factionObj;
                    if (faction == null || !faction.isActive()) continue;
                    potential[faction.index()] = true;
                }
                double pPow = DebtDiplomacyBuffer.bufferedPlayerPower();
                double cPow = 0.0;
                java.util.ArrayList<FactionNPC> willing = new java.util.ArrayList<FactionNPC>();
                for (FactionNPC faction : FACTIONS.NPCs()) {
                    if (potential[faction.index()]) {
                        cPow += (double)AD.power().get((Faction)faction);
                        willing.add(faction);
                        continue;
                    }
                    if (DIP.WAR().is(faction)) {
                        cPow += (double)AD.power().get((Faction)faction);
                        continue;
                    }
                    if (!DIP.ALLY().is(faction)) continue;
                    pPow += (double)AD.power().get((Faction)faction);
                }
                boolean recalculate = true;
                while (recalculate) {
                    recalculate = false;
                    double advantage = cPow / pPow - 1.0;
                    if (advantage < 0.0) {
                        willing.clear();
                        break;
                    }
                    for (int i = willing.size() - 1; i >= 0; --i) {
                        FactionNPC faction = (FactionNPC)willing.get(i);
                        if (!(ROPINION.trust().get(faction) > advantage + war.distress((Faction)faction))) continue;
                        double power = faction.offensivePower();
                        cPow -= power;
                        if (DIP.get((FactionNPC)faction).ally) {
                            pPow += power;
                        }
                        willing.remove(i);
                        recalculate = true;
                    }
                }
                // Zähle abgeschreckte Fraktionen (potentielle Angreifer, die NICHT in willing sind).
                int totalPotential = 0;
                for (FactionNPC faction : FACTIONS.NPCs()) {
                    if (potential[faction.index()] && !DIP.ALLY().is(faction)) totalPotential++;
                }
                this.deterredCount = Math.max(0, totalPotential - willing.size());

                // Phase 4.5: ALLE Reflection-Zugriffe (sowohl Read der bits/list
                // als auch Write von pPow/cPow/upI) gehen jetzt durch den Adapter.
                // Aufrufer manipuliert die mutable Container in-place und setzt
                // die numerischen Felder atomar.
                Bitmap1D bits = this.diplomacyAdapter.getWillingBits(war);
                // Raw-Type notwendig: snake2d.util.sets.ArrayList ist intern Object-basiert,
                // getWillingList returns ArrayList<?> — the element type is erased at runtime.
                // We MUST add FactionNPC objects to it; wildcard prevents add().
                @SuppressWarnings("rawtypes")
                ArrayList cached = this.diplomacyAdapter.getWillingList(war);
                if (bits != null && cached != null) {
                    bits.clear();
                    cached.clearSloppy();
                    for (FactionNPC faction : willing) {
                        bits.set(faction.index(), true);
                        cached.add((Object) faction);
                    }
                    this.diplomacyAdapter.setNumericState(war, GAME.updateI(), pPow, cPow);
                }

                // Sichtbarkeit: Chronik-Eintrag wenn sich die Abschreckung ändert (max 1× pro Saison).
                int season = game.time.TIME.seasons().bitsSinceStart();
                if (this.deterredCount != this.lastLoggedCount && season != this.lastLoggedSeason) {
                    this.lastLoggedCount = this.deterredCount;
                    this.lastLoggedSeason = season;
                    if (this.deterredCount > 0) {
                        EventLog.log("DIPLO", "Staatsverschuldung schreckt " + this.deterredCount + " Fraktion(en) von Krieg ab (Puffer: " + CompactNumber.format((int)(EconConfig.diplomacyDebtThreshold / 1000L)) + "k Denari).");
                    }
                }
                if (this.runtimeErrorLogged) {
                    EventLog.log("DIPLO", "Diplomatie-Schulden-Puffer wieder aktiv.");
                    this.runtimeErrorLogged = false;
                }
            }
            catch (RuntimeException e) {
                // ReflectiveOperationException wird hier NICHT direkt geworfen —
                // der ISyxDiplomacy-Adapter kapselt die Reflection und deaktiviert sich
                // bei Fehler (siehe VanillaDiplomacyAdapter.runtimeFailed). Dieser Catch
                // ist defensiv gegen reine RuntimeException-Pfade aus dem Wirtschafts-Bereich
                // (z. B. Division durch Null in cPow / pPow).
                if (this.runtimeErrorLogged) break block17;
                EventLog.log("SEAM", "Diplomatie-Schulden-Puffer versucht erneut nach: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                this.runtimeErrorLogged = true;
            }
        }
    }

    private static double bufferedPlayerPower() {
        Player player = FACTIONS.player();
        double power = AD.power().get((Faction)player);
        power += DebtDiplomacyBuffer.availableMercenaryPower(DebtDiplomacyBuffer.effectiveCredits(player.credits().getD(), EconConfig.diplomacyDebtThreshold));
        if (player.capitolRegion() != null) {
            power += RD.MILITARY().power.getD(player.capitolRegion());
        }
        return Math.max(power -= SETT.INVADOR().invadingPower(), 0.0);
    }

    private static double availableMercenaryPower(int credits) {
        double power = 0.0;
        for (int i = 0; i < AD.mercenaries().max() && credits > 0; ++i) {
            WDivMercenary mercenary = AD.mercenaries().get(i);
            if (mercenary.army() != null || mercenary.disbanded()) continue;
            int cost = AD.mercenaries().signingCost(i) + AD.mercenaries().upkeepCost(i) * 16;
            double affordable = cost <= 0 ? 1.0 : Math.min(1.0, (double)credits / (double)cost);
            power += GAME.battle().power.get((DIV_SPEC)mercenary);
            credits -= (int)Math.ceil((double)cost * affordable);
        }
        return power;
    }

    static int effectiveCredits(double treasury, long debtThreshold) {
        if (!(treasury > (double)debtThreshold)) {
            return 0;
        }
        double effective = treasury - (double)debtThreshold;
        if (effective >= 2.147483647E9) {
            return Integer.MAX_VALUE;
        }
        if (effective <= 0.0 || Double.isNaN(effective)) {
            return 0;
        }
        return (int)effective;
    }
}

