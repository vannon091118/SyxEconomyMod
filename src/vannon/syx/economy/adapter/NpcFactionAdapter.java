package vannon.syx.economy.adapter;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.npc.FactionNPC;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.lang.invoke.MethodHandles;
import snake2d.util.sets.LIST;
import settlement.entity.humanoid.Humanoid;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.ClassResolver;
import vannon.syx.economy.adapter.seam.FieldAccessor;
import vannon.syx.economy.core.EventLog;

/**
 * V71.44-Adapter for NPC faction price/resource access.
 *
 * <p>Uses {@link BypassGate} for the package-private
 * {@code NPCResources$FactionResource} inner class (private int priceSell/priceBuy fields)
 * and {@link ClassResolver} for class loading via the game ClassLoader.</p>
 *
 * <p>Derived from DeepSeek template {@code BypassGateFactory.NpcPriceAdapter}.
 * Adapted to SyxEconomyMod's BypassGate SDK (VarHandle primary, Reflection fallback)
 * instead of the template's {@code ReflectionUtil} + {@code SchemaResolver}.</p>
 *
 * <p>All-or-nothing: if the FactionResource class or its fields can't be resolved,
 * {@link #isAvailable()} returns false and all methods become no-ops.</p>
 */
public final class NpcFactionAdapter implements ISyxNpc {

    private static final String FACTION_RESOURCE_CLASS = "game.faction.npc.NPCResources$FactionResource";
    private static final String PRICE_SELL_FIELD = "priceSell";
    private static final String PRICE_BUY_FIELD  = "priceBuy";

    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = Humanoid.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    private final FieldAccessor.IntField priceSellAccessor;
    private final FieldAccessor.IntField priceBuyAccessor;
    private final boolean initOk;

    private boolean runtimeFailed;
    private boolean runtimeFailedLogged;

    public NpcFactionAdapter() {
        BypassGate gate = new BypassGate("NpcFactionAdapter", MethodHandles.lookup());
        ClassResolver resolver = gate.classResolver(GAME_CL);

        FieldAccessor.IntField sell = null;
        FieldAccessor.IntField buy  = null;
        boolean ok = false;
        try {
            Class<?> frClass = resolver.resolve(FACTION_RESOURCE_CLASS);
            sell = gate.intField(frClass, PRICE_SELL_FIELD);
            buy  = gate.intField(frClass, PRICE_BUY_FIELD);
            ok = gate.isAvailable();
        } catch (Throwable t) {
            ok = false;
            EventLog.log("SEAM", "NpcFactionAdapter init failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". NPC-Preis-Kontrolle inaktiv.");
        }

        this.priceSellAccessor = sell;
        this.priceBuyAccessor  = buy;
        this.initOk = ok;

        if (this.initOk) {
            EventLog.log("SEAM", "NpcFactionAdapter: READY (priceSell/priceBuy via BypassGate)");
        }
    }

    @Override
    public boolean isAvailable() {
        return this.initOk && !this.runtimeFailed;
    }

    @Override
    public int npcCount() {
        LIST<FactionNPC> npcs = FACTIONS.NPCs();
        return npcs != null ? npcs.size() : 0;
    }

    @Override
    public int getSellPrice(String resourceKey) {
        if (!isAvailable()) return 0;
        Object fr = getFactionResource(resourceKey);
        if (fr == null) return 0;
        try {
            return priceSellAccessor.get(fr);
        } catch (Throwable t) {
            logRuntime("getSellPrice", t);
            return 0;
        }
    }

    @Override
    public void setSellPrice(String resourceKey, int price) {
        if (!isAvailable()) return;
        LIST<FactionNPC> npcs = FACTIONS.NPCs();
        if (npcs == null) return;
        for (int i = 0; i < npcs.size(); i++) {
            FactionNPC npc = npcs.get(i);
            Object fr = getFactionResource(npc, resourceKey);
            if (fr == null) continue;
            try {
                priceSellAccessor.set(fr, price);
            } catch (Throwable t) {
                logRuntime("setSellPrice", t);
                return;
            }
        }
    }

    @Override
    public int getBuyPrice(String resourceKey) {
        if (!isAvailable()) return 0;
        Object fr = getFactionResource(resourceKey);
        if (fr == null) return 0;
        try {
            return priceBuyAccessor.get(fr);
        } catch (Throwable t) {
            logRuntime("getBuyPrice", t);
            return 0;
        }
    }

    @Override
    public void setBuyPrice(String resourceKey, int price) {
        if (!isAvailable()) return;
        LIST<FactionNPC> npcs = FACTIONS.NPCs();
        if (npcs == null) return;
        for (int i = 0; i < npcs.size(); i++) {
            FactionNPC npc = npcs.get(i);
            Object fr = getFactionResource(npc, resourceKey);
            if (fr == null) continue;
            try {
                priceBuyAccessor.set(fr, price);
            } catch (Throwable t) {
                logRuntime("setBuyPrice", t);
                return;
            }
        }
    }

    @Override
    public double getTreasury() {
        LIST<FactionNPC> npcs = FACTIONS.NPCs();
        if (npcs == null || npcs.size() == 0) return 0.0;
        return npcs.get(0).credits().credits();
    }

    @Override
    public void incTreasury(double amount, String rtypeName) {
        LIST<FactionNPC> npcs = FACTIONS.NPCs();
        if (npcs == null || npcs.size() == 0) return;
        try {
            FCredits.CTYPE ctype = FCredits.CTYPE.valueOf(rtypeName);
            npcs.get(0).credits().inc(amount, ctype);
        } catch (IllegalArgumentException e) {
            EventLog.log("SEAM", "NpcFactionAdapter.incTreasury: unknown CTYPE '" + rtypeName + "'");
        }
    }

    // ─── Internal helpers ──────────────────────────────────────────────

    /** Get FactionResource for the FIRST NPC faction (convenience). */
    private Object getFactionResource(String resourceKey) {
        LIST<FactionNPC> npcs = FACTIONS.NPCs();
        if (npcs == null || npcs.size() == 0) return null;
        return getFactionResource(npcs.get(0), resourceKey);
    }

    /** Get FactionResource for a specific NPC faction (uses reflection — type varies per engine version). */
    private Object getFactionResource(FactionNPC npc, String resourceKey) {
        try {
            RESOURCE resource = findResource(resourceKey);
            if (resource == null) return null;
            // npc.res() returns NPCResources (or FResources in some engine versions).
            // The get(RESOURCE) method exists on the return type, but the compiler
            // can't guarantee it. Use reflection for version-safe access.
            Object resObj = npc.res();
            java.lang.reflect.Method getMethod = resObj.getClass().getMethod("get", RESOURCE.class);
            return getMethod.invoke(resObj, resource);
        } catch (Throwable t) {
            logRuntime("getFactionResource", t);
            return null;
        }
    }

    /** Find RESOURCE by key string (iterates RESOURCES.ALL()). */
    private static RESOURCE findResource(String resourceKey) {
        LIST<RESOURCE> all = RESOURCES.ALL();
        for (int i = 0; i < all.size(); i++) {
            RESOURCE r = all.get(i);
            if (resourceKey.equals(r.key)) return r;
        }
        return null;
    }

    private void logRuntime(String method, Throwable t) {
        if (!this.runtimeFailedLogged) {
            this.runtimeFailedLogged = true;
            this.runtimeFailed = true;
            EventLog.log("SEAM", "NpcFactionAdapter." + method + " failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". NPC-Adapter dauerhaft inaktiv.");
        }
    }
}
