package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.Faction;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.sets.LIST;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase-5d (Plan-Task 9) — Foreign-Faction-Trade-Ledger.
 *
 * <p>Aggregates per-day credit-flow of all non-player factions as a low-fidelity
 * trade-flow proxy. Used by the mod economy for early-game income-source
 * diversification (matching the user's "Fraktions-Trade als früh-bare Einnahme").</p>
 *
 * <p><b>Vanilla-API contract</b> (verified 2026-07-24 via
 * {@code info/SongsOfSyx-sources.jar} in V71.44):</p>
 * <ul>
 *   <li>{@link FACTIONS#active()} — {@code LIST<Faction>} of NPCs only (player excluded),
 *       further filtered to {@code isActive()} (skip-realm-without-capitol); rebuilt per
 *       call by scanning the {@code npcsActive} field internally.</li>
 *   <li>{@link Faction#credits()} — {@code FCredits} for read-only sampling</li>
 *   <li>{@link Faction#credits()}.{@code credits()} — current balance as {@code double}</li>
 *   <li>{@link FACTIONS#name(Faction)} — static, returns {@code CharSequence} identifier</li>
 * </ul>
 *
 * <p><b>Read-only</b> by design — never mutates vanilla faction state. Save/Load via
 * standard chunked-Saveable pattern (FilePutter + FileGetter). No reflection.</p>
 *
 * <p><b>Future Phase-6 alternative:</b> A {@code FactionActivityListener}-derived
 * hook could give per-faction add/remove callbacks vs. the daily polling we use here.
 * Defer until pricing-precision is needed (proxy today reflects wealth-growth, which
 * correlates with but does not equal trade-flow).</p>
 */
public final class ForeignTradeLedger implements Saveable {

    /** Snapshot from the previous {@link #dailyTick} call (not "most recent" — it's yesterday's data). */
    private final Map<String, Long> previousCreditSnapshot = new HashMap<>();
    private long totalToday = 0L;
    private int activeFactionCount = 0;
    private long lastTickRun = -1L;

    /**
     * Sum of positive credit-delta across all active foreign factions in last 24h.
     * <p><b>Lifecycle:</b> zeroed on {@link #load}, recomputed on next {@link #dailyTick}.
     * Reading this getter between load and the next tick returns 0 by design.</p>
     */
    public long todaysInflow() {
        return totalToday;
    }

    /** Count of active foreign factions at last tick (player excluded by {@code active()}). */
    public int activeFactionCount() {
        return activeFactionCount;
    }

    /** Previous-tick credit state per faction — diagnostic for debug overlay. */
    public Map<String, Long> snapshotDebug() {
        return new HashMap<>(previousCreditSnapshot);
    }

    /**
     * Iterates {@link FACTIONS#active()} (NPCs only — player + inactive factions already
     * excluded by the engine), samples each faction's {@code credits()}, accumulates the
     * positive delta since the last call. Called once per in-game day from EconomySim.
     *
     * <p><b>First-call semantics:</b> When {@code previousCreditSnapshot} is empty (first
     * call after construction, save/load, or {@link #clear}), all deltas are undefined
     * and {@link #totalToday} resolves to {@code 0} for that boundary. The first
     * observed positive inflow appears at the second day-boundary. This is by design —
     * the inflow getter reports "real deltas vs. yesterday" rather than "raw wealth."</p>
     *
     * @param currentTick mod-side tick counter, used to skip re-runs within same tick
     */
    public void dailyTick(long currentTick) {
        if (currentTick == lastTickRun) return;

        Map<String, Long> currentSnapshot = new HashMap<>();
        long sumDelta = 0L;
        int count = 0;

        LIST<Faction> npcs = FACTIONS.active();
        int anonCounter = 0;
        for (Faction f : npcs) {
            CharSequence cs = FACTIONS.name(f);
            String name;
            if (cs == null) {
                name = "_anon_" + (anonCounter++);
            } else {
                name = cs.toString();
            }
            long credits = (long) f.credits().credits();
            currentSnapshot.put(name, credits);
            ++count;

            Long prev = previousCreditSnapshot.get(name);
            if (prev != null) {
                long delta = credits - prev;
                if (delta > 0) {
                    sumDelta += delta;
                }
            }
        }

        totalToday = sumDelta;
        activeFactionCount = count;
        previousCreditSnapshot.clear();
        previousCreditSnapshot.putAll(currentSnapshot);
        lastTickRun = currentTick;
    }

    @Override
    public void save(FilePutter file) {
        file.l(lastTickRun);
        file.i(previousCreditSnapshot.size());
        for (Map.Entry<String, Long> e : previousCreditSnapshot.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                throw new IllegalStateException("null faction name in credit snapshot");
            }
            file.chars(key);
            file.l(e.getValue());
        }
    }

    @Override
    public void load(FileGetter file) throws IOException {
        previousCreditSnapshot.clear();
        totalToday = 0L;
        activeFactionCount = 0;
        long tick = file.l();
        int count = file.i();
        if (count < 0 || count > 1000) {
            throw new IOException("invalid foreign-faction credit-snapshot size: " + count);
        }
        for (int i = 0; i < count; ++i) {
            String name = file.chars();
            if (name == null || name.isEmpty()) {
                throw new IOException("empty foreign-faction name during load");
            }
            previousCreditSnapshot.put(name, file.l());
        }
        lastTickRun = tick;
    }

    /** Reset state for save/load or test-isolation. */
    public void clear() {
        previousCreditSnapshot.clear();
        totalToday = 0L;
        activeFactionCount = 0;
        lastTickRun = -1L;
    }
}
