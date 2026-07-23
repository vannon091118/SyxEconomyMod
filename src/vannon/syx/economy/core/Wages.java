package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.type.HCLASSES;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import snake2d.LOG;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.rnd.RND;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class Wages implements Saveable {
    private final HashMap<String, Integer> wageByRoom = new HashMap<>();
    private final HashSet<Induvidual> unpaid = new HashSet<>();
    private int lastSeason = -1;
    private int lastPayrollDue = 0;
    private int lastPayrollPaid = 0;
    private int lastWorkersPaid = 0;
    private int lastWorkersUnpaid = 0;
    private final HashMap<String, int[]> realized = new HashMap<>();

    public int realizedWage(RoomBlueprintImp room) {
        if (room == null) {
            return -1;
        }
        int[] v = this.realized.get(room.key);
        if (v == null || v[0] <= 0) {
            return -1;
        }
        return v[1] / v[0];
    }

    public int wageOf(RoomBlueprintImp room) {
        if (room == null) {
            return 0;
        }
        Integer w = this.wageByRoom.get(room.key);
        return w == null ? EconConfig.defaultWage : w;
    }

    /**
     * Phase 5e: Wage-Setter mit State-Ownership-Gate (canonical entry-point).
     * Lehnt den Call ab (no-op + stderr-Warning) wenn
     * {@link EconConfig#stateFundedWageRegulationOnly}=true UND der Raum
     * kein state-funded-public-works ist. Blueprint=null = no-gate
     * (legacy-Modus, Caller hat Blueprint nicht aufgelöst). Caller
     * (z. B. EconomyWindow UI-Slider) muss Blueprint explizit mitgeben
     * damit sein Setz-Versuch gegen den State-Ownership-Check läuft.
     * <p>Vor-Phase-5e-Spec: "Wages.setWage() lehnt ab wenn room nicht
     * STATE_FUNDED_PUBLIC_WORKS". Vorher war setWage(String,int)
     * Blueprint-unaware — jetzt Blueprint-aware, Gate-Fire ist explizit.
     */
    public void setWage(String roomKey, int wage, RoomBlueprintImp blueprint) {
        if (EconConfig.stateFundedWageRegulationOnly && blueprint == null) {
            System.err.println("[ECON] setWage called with null blueprint on room=" + roomKey
                    + " while stateFundedWageRegulationOnly=true — gate cannot verify"
                    + " state-funding. Resolve blueprint via SETT.ROOMS()/roomKey first."
                    + " Accepting as legacy fallback (tighten to reject in Phase-5i/6"
                    + " if null-blueprint-calls become a real pattern).");
        }
        if (EconConfig.stateFundedWageRegulationOnly
                && blueprint != null
                && !EconomicRoles.stateFundedPublicWorks(blueprint)) {
            System.err.println("[ECON] setWage rejected: room=" + roomKey
                    + " is not STATE_FUNDED_PUBLIC_WORKS (stateFundedWageRegulationOnly=true,"
                    + " wageMax=" + EconConfig.wageMax + ") — toggle"
                    + " EconConfig.stateFundedWageRegulationOnly=false to bypass gate,"
                    + " or migrate to setWageIfStateFunded() for explicit accept/reject probing.");
            return;
        }
        if (wage < 0) {
            wage = 0;
        }
        if (wage > EconConfig.wageMax) {
            wage = EconConfig.wageMax;
        }
        wage = wage / EconConfig.wageStep * EconConfig.wageStep;
        this.wageByRoom.put(roomKey, wage);
    }

    /**
     * Phase 5e: Wage-Setter mit State-Ownership-Gate. Lehnt den Call ab (no-op +
     * stderr-Warning) wenn {@link EconConfig#stateFundedWageRegulationOnly}=true
     * UND der Raum kein state-funded-public-works ist. Caller (z. B. EconomyWindow
     * UI-Slider) muss explizit diesen State-Mode-Pfad wählen.
     *
     * @return {@code true} wenn gesetzt, {@code false} wenn durch Gate abgelehnt.
     */
    public boolean setWageIfStateFunded(String roomKey, int wage, RoomBlueprintImp blueprint) {
        if (blueprint == null) {
            System.err.println("[ECON] setWageIfStateFunded rejected: blueprint=null for room="
                    + roomKey + " — caller did not resolve room-key first, or stale UI-state.");
            return false;
        }
        if (EconConfig.stateFundedWageRegulationOnly
                && !EconomicRoles.stateFundedPublicWorks(blueprint)) {
            System.err.println("[ECON] setWageIfStateFunded rejected: room="
                    + roomKey + " is not STATE_FUNDED_PUBLIC_WORKS "
                    + "(stateFundedWageRegulationOnly=true, wageMax=" + EconConfig.wageMax
                    + ") — toggle EconConfig.stateFundedWageRegulationOnly=false to use generic setWage().");
            return false;
        }
        this.setWage(roomKey, wage, blueprint);
        return true;
    }

    /** Phase 5e: API-Gate für UI-Code — gibt true zurück wenn Caller regulieren darf. */
    public boolean canRegulateWage(RoomBlueprintImp blueprint) {
        return !EconConfig.stateFundedWageRegulationOnly
                || EconomicRoles.stateFundedPublicWorks(blueprint);
    }

    public boolean isUnpaid(Induvidual indu) {
        return this.unpaid.contains(indu);
    }

    public int lastPayrollDue() {
        return this.lastPayrollDue;
    }

    public int lastPayrollPaid() {
        return this.lastPayrollPaid;
    }

    public int lastWorkersPaid() {
        return this.lastWorkersPaid;
    }

    public int lastWorkersUnpaid() {
        return this.lastWorkersUnpaid;
    }

    public int update(Roster roster, Wallets wallets) {
        if (!EconConfig.wagesEnabled) {
            return 0;
        }
        if (SETT.ROOMS() == null) {
            return 0;
        }
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastSeason == -1) {
            this.lastSeason = season;
            return 0;
        }
        if (season == this.lastSeason) {
            return 0;
        }
        this.lastSeason = season;
        return this.payday(roster, wallets);
    }

    private int payday(Roster roster, Wallets wallets) {
        this.unpaid.clear();
        this.realized.clear();
        int n = roster.size();
        if (n == 0) {
            return 0;
        }
        // Minimum-Lohn: Arbeiten muss IMMER mehr einbringen als Nichtstun.
        // Deckelt nach oben durch wageMax, nach unten durch handoutWalletAmount.
        int wageFloor = Math.min(EconConfig.wageMax,
                Math.max(EconConfig.defaultWage, EconConfig.handoutWalletAmount));
        int offset = RND.rInt((int)n);
        double treasury = FACTIONS.player().credits().credits();
        int budget = (int)Math.max(0.0, treasury);
        int due = 0;
        int paid = 0;
        int workersPaid = 0;
        int workersUnpaid = 0;
        for (int i = 0; i < n; ++i) {
            RoomBlueprintImp job;
            Humanoid h = roster.get((i + offset) % n);
            Induvidual indu = h.indu();
            if (!EconConfig.payWagesToSlaves && indu.clas() == HCLASSES.SLAVE() || (job = (RoomBlueprintImp)STATS.WORK().profession.get(indu)) == null) continue;
            int wage = Math.max(wageFloor, this.wageOf(job));
            int[] rec = this.realized.computeIfAbsent(job.key, k -> new int[2]);
            rec[0] = rec[0] + 1;
            if (wage <= 0) continue;
            due += wage;
            if (wallets.wasPaidThisTick(indu)) {
                continue;
            }
            if (budget >= wage) {
                budget -= wage;
                paid += wage;
                ++workersPaid;
                rec[1] = rec[1] + wage;
                wallets.add(h, wage);
                wallets.markPaidThisTick(indu);
                continue;
            }
            ++workersUnpaid;
            this.unpaid.add(indu);
        }
        if (paid > 0) {
            FACTIONS.player().credits().inc((double)(-paid), FCredits.CTYPE.MISC);
        }
        this.lastPayrollDue = due;
        this.lastPayrollPaid = paid;
        this.lastWorkersPaid = workersPaid;
        this.lastWorkersUnpaid = workersUnpaid;
        if (due > 0) {
            LOG.ln("[ECON] payday: due=" + due + " paid=" + paid + " workers=" + workersPaid + (String)(workersUnpaid > 0 ? " UNPAID=" + workersUnpaid + " (INSOLVENT)" : "") + " treasury=" + (int)FACTIONS.player().credits().credits());
        }
        return paid;
    }

    public void save(FilePutter file) {
        file.i(2);
        file.i(this.lastSeason);
        file.i(this.wageByRoom.size());
        for (Map.Entry<String, Integer> entry : this.wageByRoom.entrySet()) {
            file.chars((CharSequence)entry.getKey());
            file.i(entry.getValue().intValue());
        }
        file.i(this.realized.size());
        for (Map.Entry<String, int[]> entry : this.realized.entrySet()) {
            file.chars((CharSequence)entry.getKey());
            file.i(entry.getValue()[0]);
            file.i(entry.getValue()[1]);
        }
    }

    /**
     * Hydrates wageByRoom directly with file content — bypasses setWage()
     * deliberately so the State-Ownership-Gate (Phase 5e) cannot fire at
     * hydration. Pre-Phase-5e saves don't carry blueprint data; routing through
     * setWage(key, val, null) would silently backdoor the gate per its null-blueprint
     * legacy behavior. Direct population keeps load/save idempotent.
     */
    public void load(FileGetter file) throws IOException {
        int version = file.i();
        this.lastSeason = file.i();
        this.wageByRoom.clear();
        int am = file.i();
        for (int i = 0; i < am; ++i) {
            String key = file.chars();
            int wage = file.i();
            this.wageByRoom.put(key, wage);
        }
        this.realized.clear();
        if (version >= 2) {
            int rm = file.i();
            for (int i = 0; i < rm; ++i) {
                String key = file.chars();
                int owed = file.i();
                int paidSum = file.i();
                this.realized.put(key, new int[]{owed, paidSum});
            }
        }
        this.unpaid.clear();
    }

    public void reset() {
        this.wageByRoom.clear();
        this.unpaid.clear();
        this.realized.clear();
        this.lastSeason = -1;
        this.lastWorkersUnpaid = 0;
        this.lastWorkersPaid = 0;
        this.lastPayrollPaid = 0;
        this.lastPayrollDue = 0;
    }
}

