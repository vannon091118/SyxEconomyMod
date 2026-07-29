package vannon.syx.economy.core;

import init.type.CRIMES;
import init.type.CRIMES.CRIME;
import init.type.HCLASSES;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.stats.STATS;
import snake2d.util.rnd.RND;

/**
 * Theft crime consumer: proximity-pair encounters where desperate citizens
 * rob wealthier ones.
 *
 * <p>Follows the vanilla crime pipeline:
 * <ol>
 *   <li>Check desperation (thief money &lt; threshold, victim money &gt; threshold)</li>
 *   <li>Calculate theft chance (base × guard deterrence × random)</li>
 *   <li>Transfer money: victim → thief via {@code wallets.add()}</li>
 *   <li>Report to vanilla: {@code STATS.LAW().prisonerType.set()} + {@code reportCriminal()}</li>
 * </ol>
 *
 * <p>Conservation: money is NOT created or destroyed — it moves from one wallet to another.
 * The theft amount is clamped to the victim's current balance.</p>
 */
public final class CrimeTheftConsumer implements PairSource.PairConsumer {

    private final Wallets wallets;

    // Encounter count: theft runs with the same `n` as exchange, doubling total Phase 9
    // encounters (2n). At encountersPerGameSecond=200, that's ~13 callbacks/frame at 60fps.

    /** Diagnostic counter: total thefts this session. Reset by DiagnosticExporter. */
    static int theftsThisDay = 0;
    /** Diagnostic counter: total denari stolen this session. */
    static long stolenThisDay = 0L;
    /** Diagnostic counter: total theft reports sent to guards. */
    static int reportsSentThisDay = 0;

    CrimeTheftConsumer(Wallets wallets) {
        this.wallets = wallets;
    }

    @Override
    public void pair(Humanoid a, Humanoid b) {
        if (!EconConfig.crimeTheftEnabled) return;

        // Determine who is the thief (poorer) and who is the victim (richer).
        // Swap if needed so that 'a' = thief, 'b' = victim.
        int moneyA = wallets.get(a);
        int moneyB = wallets.get(b);
        if (moneyA > moneyB) {
            Humanoid tmp = a; a = b; b = tmp;
            int tmpM = moneyA; moneyA = moneyB; moneyB = tmpM;
        }
        // Now: moneyA <= moneyB  (a is the potential thief)

        // Gate checks — thief side
        if (moneyA > EconConfig.crimeThiefMaxMoney) return;     // not desperate enough
        if (a.indu().clas() == HCLASSES.SLAVE()) return;        // Slaves excluded: vanilla tracks slave crimes separately via CRIMES.S_THEFT(). A slave theft feature would need its own consumer with the S_THEFT crime type.
        if (a.indu().hostile()) return;                         // enemies/rioters excluded

        // Gate checks — victim side
        if (moneyB < EconConfig.crimeTheftVictimMinMoney) return; // victim too poor

        // Guard deterrence: more guards = less crime
        double guardRatio = 0.0;
        try {
            int totalPop = STATS.POP().pop((init.race.Race) null, null);
            int guardCount = STATS.POP().pop((init.race.Race) null, init.type.HTYPES.GUARD());
            if (totalPop > 0) {
                guardRatio = (double) guardCount / totalPop;
            }
        } catch (Throwable t) {
            // Engine not ready or HTYPES not available — no deterrence
            guardRatio = 0.0;
        }

        double chance = EconConfig.crimeTheftChanceBase
            * Math.max(0.0, 1.0 - EconConfig.crimeTheftGuardDeterrence * guardRatio);

        if (RND.rFloat() >= chance) return;

        // ── Theft occurs ──────────────────────────────────────────
        int maxSteal = (int)((long)moneyB * EconConfig.crimeTheftMaxFractionBp / 10000L);
        if (maxSteal <= 0) return;
        int stolen = 1 + RND.rInt(maxSteal); // at least 1 D, up to maxSteal
        if (stolen > moneyB) stolen = moneyB;

        // Transfer: victim → thief (conservation-safe)
        wallets.add(b, -stolen);
        wallets.add(a, stolen);

        // ── Vanilla crime pipeline ────────────────────────────────
        try {
            CRIME crime = CRIMES.THEFT();
            // Mark the thief as having committed theft
            STATS.LAW().prisonerType.set(a.indu(), crime);
            // Increment crime statistics
            STATS.LAW().crimes.get(crime.index()).commit(a.indu());
            // Report to guards (with configured chance)
            if (RND.rFloat() < EconConfig.crimeTheftReportChance) {
                SETT.ROOMS().GUARD.reporter.reportCriminal(a);
                reportsSentThisDay++;
            }
        } catch (Throwable t) {
            // Engine not ready — vanilla API unavailable, skip crime pipeline
            // Money transfer already happened (the thief got away this time)
        }

        // ── Diagnostics + Chronicle ──────────────────────────────
        EventLog.logSampled("CRIME",
            "Diebstahl: " + stolen + " D von Bürger #" + b.id()
            + " durch #" + a.id() + " (Geld: " + moneyA + "→" + (moneyA + stolen) + ")");
        theftsThisDay++;
        stolenThisDay += stolen;
    }

    /** Called by DiagnosticExporter at end of day to read + reset counters.
     *  Returns {thefts, reports, stolenDenari}. stolenDenari is clamped to int range. */
    static int[] drainCounters() {
        int[] result = {
            theftsThisDay,
            reportsSentThisDay,
            (int) Math.min(Integer.MAX_VALUE, stolenThisDay)
        };
        theftsThisDay = 0;
        stolenThisDay = 0L;
        reportsSentThisDay = 0;
        return result;
    }
}
