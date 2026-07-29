package vannon.syx.economy.core;

import init.type.CRIMES;
import init.type.CRIMES.CRIME;
import init.type.HCLASSES;
import init.type.HTYPES;
import init.race.Race;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import init.type.CRIME_PUNISHMENTS;
import settlement.stats.STATS;
import snake2d.util.rnd.RND;

import java.util.HashMap;
import java.util.Map;

/**
 * Theft crime consumer: proximity-pair encounters where desperate citizens
 * rob wealthier ones.
 *
 * <p>Follows the vanilla crime pipeline:
 * <ol>
 *   <li>Check desperation (thief money &lt; threshold, victim money &gt; threshold)</li>
 *   <li>Calculate theft chance: base × moneyFactor × guardFactor × (1 − deterrence × guardRatio)</li>
 *   <li>Transfer money: victim → thief via {@code wallets.add()}</li>
 *   <li>Report to vanilla: {@code STATS.LAW().prisonerType.set()} + {@code reportCriminal()}</li>
 *   <li>After N thefts: per-class/race policy → {@link CRIME_PUNISHMENTS#EXECUTE()} for deterrence</li>
 * </ol>
 *
 * <p>Conservation: money is NOT created or destroyed — it moves from one wallet to another.
 * The theft amount is clamped to the victim's current balance.</p>
 *
 * <p>Sprint v0.13.102+ — adaptive crime: moneyFactor repräsentiert "arm ⇒ krimineller",
 * guardFactor repräsentiert "wenig Miliz ⇒ krimineller". Beide sind multiplikative
 * Multiplikatoren auf die Basis-Chance (1.0 = neutral).</p>
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
    /** Diagnostic counter: citizens sent to arena this day (per-class policy switch). */
    static int arenaSentencesThisDay = 0;

    /** Per-individual theft counter: id() → theft-count. Drives arena escalation. */
    private final Map<Integer, Integer> thiefCounts = new HashMap<>();

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
        boolean guardsAvailable = false;
        try {
            int totalPop = STATS.POP().pop((Race) null, null);
            int guardCount = STATS.POP().pop((Race) null, HTYPES.GUARD());
            if (totalPop > 0) {
                guardRatio = (double) guardCount / totalPop;
                guardsAvailable = true;
            }
        } catch (Throwable t) {
            // Engine not ready or HTYPES not available — no deterrence
            guardRatio = 0.0;
        }

        // ── Adaptive Faktoren (Sprint v0.13.102+) ────────────────────────
        // moneyFactor: 1.0 + (1 − coverage) × strength. Armut treibt Kriminalität.
        // coverage = totalMoney / (pop × referenceWealth); < 1.0 = arm.
        double totalMoney = Math.max(0.0, wallets.circulating());
        double moneyFactor = computeMoneyFactor(totalMoney, EconConfig.population);

        // guardFactor: 1.0 + (1 − guardRatio) × strength. Weniger Miliz → mehr Krim.
        // Wenn keine Engine-Daten vorliegen (defensiv), bleiben wir bei 1.0 — sonst
        // würde engine-not-ready (guardRatio=0) zu 3× Crime-Amplification führen.
        double guardFactor = computeGuardFactor(guardRatio, guardsAvailable);

        double chance = EconConfig.crimeTheftChanceBase
            * moneyFactor
            * guardFactor
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

        // ── Arena-Abschreckung (Sprint v0.13.102+) ────────────────────────
        // Nach N Diebstählen: class/race-policy auf PUNISHMENT.ARENA setzen.
        // Vanilla's PrisonerAI routet den Delinquenten in die nächste Arena
        // (ExecuteArena + arena.work.reserveDeath). Per-class policy ist
        // pragmatischer als per-individual (kein HAI-Reflection nötig).
        if (EconConfig.crimeTheftArenaEnabled) {
            int count = thiefCounts.getOrDefault(a.id(), 0) + 1;
            thiefCounts.put(a.id(), count);
            if (count >= EconConfig.crimeTheftArenaThreshold) {
                try {
                    STATS.LAW().crimes.get(CRIMES.THEFT().index()).punishmentSet(
                            a.indu().clas(), a.indu().race(), CRIME_PUNISHMENTS.EXECUTE());
                    arenaSentencesThisDay++;
                    EventLog.log("CRIME",
                        "Arena-Straf: Bürger #" + a.id()
                        + " (" + count + ". Diebstahl) → PUNISHMENT.ARENA");
                } catch (Throwable t) {
                    // Engine not ready — fallback silently
                }
            }
        }

        // ── Diagnostics + Chronicle ──────────────────────────────
        EventLog.logSampled("CRIME",
            "Diebstahl: " + stolen + " D von Bürger #" + b.id()
            + " durch #" + a.id() + " (Geld: " + moneyA + "→" + (moneyA + stolen) + ")");
        theftsThisDay++;
        stolenThisDay += stolen;
    }

    /** Called by DiagnosticExporter at end of day to read + reset counters.
     *  Returns {thefts, reports, stolenDenari, arenaSentences}.
     *  stolenDenari is clamped to int range. */
    static int[] drainCounters() {
        int[] result = {
            theftsThisDay,
            reportsSentThisDay,
            (int) Math.min(Integer.MAX_VALUE, stolenThisDay),
            arenaSentencesThisDay
        };
        theftsThisDay = 0;
        stolenThisDay = 0L;
        reportsSentThisDay = 0;
        arenaSentencesThisDay = 0;
        return result;
    }

    // ── Adaptive-Factor-Statik (Sprint v0.13.102+) ─────────────────────
    // Ausgelagert aus pair()/capture() damit BEIDE Stellen (CrimeTheftConsumer + 
    // DiagnosticExporter) konsistente Werte produzieren. Wenn der User die Formel
    // tuned, faellt sonst ein Site durch das Raster.

    /** Money-Faktor: 1.0 + (1 − coverage) × strength. Armut treibt Kriminalität.
     *  coverage = totalMoney / (pop × referenceWealth); < 1.0 = arm.
     *  Bei strength=0.0 oder pop=0 → 1.0 (neutral). */
    static double computeMoneyFactor(double totalMoney, int pop) {
        if (EconConfig.crimeTheftMoneyFactorStrength <= 0.0) return 1.0;
        int livePop = Math.max(1, pop);
        double coverage = Math.min(1.0,
                totalMoney / (livePop * Math.max(1, EconConfig.crimeTheftReferenceWealth)));
        return 1.0 + (1.0 - coverage) * EconConfig.crimeTheftMoneyFactorStrength;
    }

    /** Guard-Faktor: 1.0 + (1 − guardRatio) × strength. Weniger Miliz → mehr Krim.
     *  Wenn {@code dataAvailable=false} (Engine nicht ready), bleibt 1.0 (neutral).
     *  Sonst wuerde guardRatio=0 (default) zu 3× Crime-Amplification fuehren. */
    static double computeGuardFactor(double guardRatio, boolean dataAvailable) {
        if (EconConfig.crimeTheftGuardFactorStrength <= 0.0) return 1.0;
        if (!dataAvailable) return 1.0;
        return 1.0 + Math.max(0.0, 1.0 - guardRatio) * EconConfig.crimeTheftGuardFactorStrength;
    }
}
