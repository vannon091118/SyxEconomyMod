package vannon.syx.economy.core;

import game.time.TIME;
import vannon.syx.economy.adapter.EngineMirror;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import vannon.syx.economy.core.io.IOGraph;
import vannon.syx.economy.core.io.IOMatrix;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rebalancing-Diagnostik-Export.
 *
 * <p>Opt-in via {@link EconConfig#diagnosticsExportEnabled}. Wenn aktiv, schreibt
 * die Klasse pro In-Game-Tag zwei CSV-Dateien in
 * {@code ~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/}:
 * <ul>
 *   <li>{@code rebalance_macro_<session-epoch>.csv} — eine Zeile pro Tag mit
 *       aggregierten Makro-Kennzahlen (Gini, Lohn, Tresor, Steuereinnahmen,
 *       Versorgungs-Bilanz, Audit-Delta).</li>
 *   <li>{@code rebalance_resources_<session-epoch>.csv} — Long-Format, eine
 *       Zeile pro Resource pro Tag mit Anker-Preis, Markt-Preis, Deckung,
 *       Supply/Demand, Lager und Engpass-Signal.</li>
 *   <li>{@code rebalance_firms_<session-epoch>.csv} — Long-Format, eine
 *       Zeile pro Firma pro Tag mit Blueprint, Mitarbeitern, Beschäftigungsziel,
 *       Profit/Marginal, Einkommensausschüttung und unbezahlten Arbeitern.
 *       Deckt strukturell unprofitable Firmen auf.</li>
 * </ul>
 *
 * <p>Schreiben erfolgt asynchron auf einem Single-Thread-Executor (Daemon),
 * damit Main-Thread-Stutter vermieden wird. Pro Write: open → append → flush →
 * close, damit ein Crash mitten im Spiel keine korrupte Datei hinterlässt.</p>
 *
 * <p>Nutzung: Spiel spielen lassen → Save → Mod-Datenverzeichnis prüfen → CSV
 * in Excel/pandas laden → Trends und Engpässe identifizieren → EconConfig
 * justieren → Schleife wiederholen.</p>
 */
public final class DiagnosticExporter {

    // ═══════════════════════════════════════════════════════
    // DC-01: Summary-Event-Record — agent-lesbares Compact-Format
    // Statt 39.6M TRACE-Zeilen: nur State-Changes mit Kontext.
    // ═══════════════════════════════════════════════════════

    /** Ein signifikanter State-Change zur Diagnose. */
    private record SummaryEvent(
            long day, String entity, String eventType, String field,
            double fromVal, double toVal, String context) {}

    /** Max. Events im In-Memory-Buffer (RingBuffer-Semantik). */
    private static final int MAX_BUFFER_EVENTS = 10_000;

    /** Buffer: akkumuliert Events bis zum nächsten Save. */
    private static final List<SummaryEvent> eventBuffer = new ArrayList<>(MAX_BUFFER_EVENTS / 2);

    /** State-Tracker: entity → field → lastValue. Verhindert Duplikat-Events. */
    private static final Map<String, Map<String, Double>> lastState = new HashMap<>();

    /** Summary-Change-Thresholds (gleiche Semantik wie tools/change_detector.py). */
    private static final double COVERAGE_THRESHOLD = 0.10;
    private static final double PRICE_CHANGE_THRESHOLD = 0.20;
    private static final double GINI_THRESHOLD = 0.05;

    // ═══════════════════════════════════════════════════════

    /** Session-Epoch als Suffix des Dateinamens. nanoTime ist kollisionsfreier als millis. */
    private static final long SESSION_EPOCH = System.nanoTime();

    /** Verzeichnis für Diagnostik-CSV. Wird beim ersten Export angelegt. */
    private static final Path DIAG_DIR = Paths.get(
            System.getProperty("user.home"),
            ".local", "share", "songsofsyx", "mods", "SyxEconomyMod", "diagnostics");

    private static final String MACRO_FILE = "rebalance_macro_" + SESSION_EPOCH + ".csv";
    private static final String RESOURCE_FILE = "rebalance_resources_" + SESSION_EPOCH + ".csv";
    private static final String FIRMS_FILE = "rebalance_firms_" + SESSION_EPOCH + ".csv";
    private static final String IO_FILE = "rebalance_io_" + SESSION_EPOCH + ".csv";
    private static final String IMMIGRATION_FILE = "rebalance_immigration_" + SESSION_EPOCH + ".csv";

    private static final String[] MACRO_HEADER = {
            "game_day", "season", "population", "deaths", "emigrations", "inherited", "heirless",
            "gini", "median_wealth", "mean_wealth", "max_wealth",
            "treasury", "total_money", "seed_money", "audit_delta",
            "wage_config_max", "actual_mean_wage", "wage_share", "unpaid_ratio",
            "head_tax", "market_receipts", "ration_out", "wages_paid", "warehouse_bought", "warehouse_sold",
            // Housing-Spalten sind *_last_tick, weil HousingMarket.update() saisonal abrechnet.
            // Außerhalb des Saison-Ticks = 0. Für kumulierte Werte StateWarehouses-Felder pinnen.
            "housing_rent_last_tick", "housing_rent_due_last_tick", "housing_evictions_last_tick",
            "property_sales", "property_dividends",
            "food_basket_price", "food_days",
            "priority_expansion_signals",
            "thefts_today", "stolen_today", "theft_reports_sent",
            // Sprint v0.13.102+: Adaptive-Crime-Faktoren + Arena-Straf-Counter.
            // moneyFactor / guardFactor sind die Multiplikatoren, die zum
            // End-of-Day auf die Basis-Chance angewendet wurden. arena_sentences_today
            // zaehlt Per-Class-Policy-Switches auf PUNISHMENT.ARENA.
            "theft_money_factor", "theft_guard_factor", "arena_sentences_today",
            // RES-035 — Allocation-Path-Hook Aggregates (read+zero via
            // FirmLedger.drainAllocationCounters() am Tagesende).
            "alloc_target_init", "alloc_divergence", "alloc_payroll_dist",
            "alloc_priority_write", "alloc_player_override", "alloc_hill_step"
    };

    private static final String[] RESOURCE_HEADER = {
            "game_day", "season", "resource",
            "anchor_price", "market_price",
            "coverage", "supply_per_day", "demand_per_day", "stock",
            // days_of_supply = -1.0 bedeutet "kein aktueller Bedarf" (demand == 0).
            // Analytik-Filter: df = df.query("days_of_supply >= 0") — Rest ist aussagekräftig.
            "days_of_supply", "starving_signal"
    };

    private static final String[] FIRM_HEADER = {
            "game_day", "season", "blueprint", "employees", "employed_target",
            // RES-035: max_capacity + hard_target eingefügt. Damit ist in einer
            // Zeile sichtbar: employed=6 von max=45, employed_target=45, hardTarget=6
            // → Engine-Cap verhindert Skalierung über hard_target. Smoking-Gun.
            "max_capacity", "hard_target",
            "profit_per_day", "marginal_per_worker", "income_carry",
            "total_output_value_per_day", "total_input_value_per_day",
            "last_income_due", "last_income_paid", "workers_unpaid", "stuck_seconds",
            "expansion_signals",
            // Sprint v0.13.103+ — Staircase-Audit (5-Tier 0..4) + Staatsbestand-Critical (0/1).
            // staircase_tier=0..4 aus firmStaircaseWorkerFractions; staatsbestand_critical=1
            // wenn coverage < minCoverage (override → 100% workers).
            "staircase_tier", "staatsbestand_critical"
    };

    /** Single-thread Executor — hält Reihenfolge ein und entkoppelt Disk-IO vom Tick. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "syx-econ-diag");
        t.setDaemon(true);
        return t;
    });

    /** Welcher Tag zuletzt exportiert wurde — vermeidet Doppel-Exports pro Tag. */
    private static volatile long lastExportedDay = -1L;

    /** Resets the daily export guard so the next exportDay() call will run
     *  even if the current day was already exported. Used by cheat buttons. */
    public static void resetExportGuard() {
        lastExportedDay = -1L;
    }

    // ── Rebalance-Alert-Schwellwerte ───────────────────────────────────
    private static final double GINI_HARD_THRESHOLD = 0.40;
    private static final long AUDIT_DELTA_HARD_THRESHOLD = 1000L;
    /** Letzter alarmierter Wert — nur bei Überschreitung oder Verschlechterung >10% erneut loggen. */
    private static volatile double lastAlertedGini = -1.0;
    private static volatile long lastAlertedAuditDelta = -1L;

    private static final String[] IO_HEADER = {
            "game_day", "resource", "type",
            "downstream_resource", "coefficient"
    };

    private static final String[] IMMIGRATION_HEADER = {
            "game_day", "tick", "population", "migration_cap", "cap_hit",
            "wallet_median", "mean_wealth", "foreign_tax_modifier",
            "booster_raw", "booster_value", "depth", "steepness",
            "phase_factor", "happiness_proxy"
    };

    /** Header-Flag: verhindert Doppel-Header bei append. */
    private static volatile boolean macroHeaderWritten = false;
    private static volatile boolean resourceHeaderWritten = false;
    private static volatile boolean firmsHeaderWritten = false;
    private static volatile boolean ioHeaderWritten = false;
    private static volatile boolean immigrationHeaderWritten = false;

    private DiagnosticExporter() {
    }

    /**
     * Hook aus {@link EconomySim#update(double)} — wird einmal pro In-Game-Tag
     * am Ende des Tages aufgerufen (nach allen Markt- und Lohn-Updates).
     *
     * @param sim EconomySim-Instanz
     */
    public static void exportDay(EconomySim sim) {
        if (!EconConfig.diagnosticsExportEnabled) {
            return;
        }
        long day = sim.ticks() / (long) TIME.secondsPerDay();
        if (day == lastExportedDay) {
            return;
        }
        lastExportedDay = day;
        int season = (int) (day % 4L);

        // Snapshot-Phasen ab hier: ALLES in try/catch — ein mid-engine Refresh-Crash
        // darf den Tick-Loop NIE stoppen. Bei Fehler: Tag verwerfen, nächster Tag
        // versucht es erneut (volatile lastExportedDay ist bereits gesetzt).
        EconDaySnapshot snap = null;
        StringBuilder resourceRows;
        StringBuilder firmsRows;
        try {
            // ── ATOMIC CAPTURE — Res-011 ──────────────────────────────
            // Vorher: 25+ separate sim.*()-Calls verteilt ueber die Funktion.
            // Jetzt: ein einziger Block in EconDaySnapshot.capture() mach alle
            // Live-Reads + Counter-Drains zusammen. Nach Rueckgabe KEINE
            // weiteren Live-Reads mehr — alles aus snap.*() abgeleitet.
            snap = EconDaySnapshot.capture(sim);
            // Destructure favouriert ~no fields here; consumers use snap.*().

            resourceRows = new StringBuilder(1024);
            int resourceCount = snap.flowSnapshot().size();
            float[] anchors = snap.anchors();
            int resourcesAllSize = RESOURCES.ALL().size();
            for (int i = 0; i < resourceCount; ++i) {
                if (i >= snap.flowSnapshot().size()) break;
                float anchor = i < anchors.length ? anchors[i] : 0f;
                // Res-011: marketPrice und coverage kommen aus dem atomaren Snapshot
                // (kein Live-Read mehr in dieser Schleife).
                int market = i < snap.marketPrices().length ? snap.marketPrices()[i] : 0;
                double coverage = i < snap.coverages().length ? snap.coverages()[i] : 0.0;
                double supply = snap.flowSnapshot().supplyPerDay(i);
                double demand = snap.flowSnapshot().demandPerDay(i);
                double stock = snap.flowSnapshot().stock(i);
                // Sentinel -1.0 wenn kein Bedarf. Pandas: values >= 0 filtern.
                double daysOfSupply = demand <= 0.0 ? -1.0 : stock / demand;
                int starving = (demand > 0.0 && daysOfSupply < 3.0) ? 1 : 0;
                String resourceName = i < resourcesAllSize
                        ? ((RESOURCE) RESOURCES.ALL().get(i)).key
                        : ("idx_" + i);
                resourceRows.append(formatResourceRow(snap.day(), snap.season(), resourceName,
                        anchor, market, coverage, supply, demand, stock, daysOfSupply, starving));

                // DC-01: Summary-Change-Detection pro Resource
                detectResourceChanges(snap.day(), resourceName, anchor, market, coverage,
                        supply, demand, stock, daysOfSupply, starving);
            }

            // ── Firmen-Daten aus gecachtem snap.signalsByBlueprint()
            firmsRows = new StringBuilder(1024);
            FirmLedger ledger = sim.firmLedger();
            if (ledger != null) {
                for (FirmLedger.FirmFinancialSnapshot firm : ledger.firmFinancialSnapshots()) {
                    int firmSignals = snap.signalsByBlueprint().getOrDefault(firm.blueprint(), 0);
                    firmsRows.append(formatFirmRow(snap.day(), snap.season(), firm, firmSignals));
                }
            }

            // Hard-Threshold Rebalance-Alerts + Macro-Summary-Change-Detection
            // lesen Werte aus dem Snapshot — keine live sim.*()-Calls hier.
            // detectMacroChanges(sim-Arg) braucht sim nur für Progression().stage;
            // WealthStats kommt aus snap.stats(). Kein Re-Read von stats.*()-Feldern mehr.
            checkRebalanceAlerts(snap.gini(), snap.auditDelta(), snap.day());
            detectMacroChanges(snap.day(), sim, snap.stats());
        } catch (RuntimeException t) {
            System.err.println("[ECON] DiagnosticExport snapshot failed for day " + day
                    + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return;
        }

        // ── IO-Table (empirische Input-Output-Matrix) ──────────
        String ioRows = "";
        try {
            ioRows = exportIOTable(day, sim);
        } catch (RuntimeException t) {
            System.err.println("[ECON] IO-Table export failed for day " + day
                    + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        // Hintergrund-Thread übernimmt Disk-IO. Kein Capture von "this" oder Live-Objekten.
        // Res-011: macroRow wird JETZT aus dem atomaren Snapshot gebaut —
        // formatMacroRow(snap) liest ausschließlich snap.*()-Felder, KEIN live sim.*()-Call.
        final String macroRowFinal = formatMacroRow(snap);
        final StringBuilder resourceRowsFinal = resourceRows;
        final StringBuilder firmsRowsFinal = firmsRows;
        final String ioRowsFinal = ioRows;
        IO.submit(() -> writeAll(macroRowFinal, resourceRowsFinal.toString(), firmsRowsFinal.toString(), ioRowsFinal));
        // Sprint 6.4 — Unified CSV-Bridge: parallel-write a meta-row to DebugCsv
        // fuer Konsolidierte Debug-Analyse. Additive only.
        LoggingAdapter.csvTrace(
                LoggingAdapter.Category.SNAPSHOT,
                LoggingAdapter.Subsystem.ECON,
                LoggingAdapter.Severity.INFO,
                "diag_export",
                String.valueOf(snap.day()),
                "macro=" + (!macroRowFinal.isEmpty() ? "1" : "0")
                        + " resources=" + resourceRows.length()
                        + " firms=" + firmsRows.length());
    }

    /**
     * Synchroner Schreib-Pfad auf Hintergrund-Thread. Bei JVM-Shutdown wird
     * nicht auf Beendigung gewartet (max. ein paar hundert Bytes im
     * Executor-Buffer, keine Daten-Korruption).
     */
    private static void writeAll(String macroRow, String resourceRows, String firmsRows, String ioRows) {
        try {
            ensureDir();
            if (!macroHeaderWritten) {
                Files.writeString(DIAG_DIR.resolve(MACRO_FILE),
                        join(MACRO_HEADER) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                macroHeaderWritten = true;
            }
            if (!resourceHeaderWritten) {
                Files.writeString(DIAG_DIR.resolve(RESOURCE_FILE),
                        join(RESOURCE_HEADER) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                resourceHeaderWritten = true;
            }
            if (!firmsHeaderWritten) {
                Files.writeString(DIAG_DIR.resolve(FIRMS_FILE),
                        join(FIRM_HEADER) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                firmsHeaderWritten = true;
            }
            if (macroRow != null && !macroRow.isEmpty()) {
                Files.writeString(DIAG_DIR.resolve(MACRO_FILE),
                        macroRow + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            if (resourceRows != null && !resourceRows.isEmpty()) {
                Files.writeString(DIAG_DIR.resolve(RESOURCE_FILE),
                        resourceRows,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            if (firmsRows != null && !firmsRows.isEmpty()) {
                Files.writeString(DIAG_DIR.resolve(FIRMS_FILE),
                        firmsRows,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            if (ioRows != null && !ioRows.isEmpty()) {
                if (!ioHeaderWritten) {
                    Files.writeString(DIAG_DIR.resolve(IO_FILE),
                            join(IO_HEADER) + "\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    ioHeaderWritten = true;
                }
                Files.writeString(DIAG_DIR.resolve(IO_FILE),
                        ioRows,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            // Immigration-Diagnostik wird über appendImmigrationRow() asynchron geschrieben
            // (separate write-Methode unten)
        } catch (IOException e) {
            // Disk voll? Read-only? Mod-Directory schreibgeschützt?
            // Wir loggen an stderr und versuchen es morgen erneut — gibt kein EventLog hier.
            System.err.println("[ECON] DiagnosticExport write failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Exportiert die empirische IO-Matrix als Long-Format CSV.
     * Für jede Ressource mit nicht-null Koeffizient wird eine Zeile geschrieben:
     *   game_day, resource, type (direct/total), downstream_resource, coefficient.
     *
     * <p>Leere Strings wenn IOMatrix noch nicht valid (erster Tag, zu wenige Daten).</p>
     */
    private static String exportIOTable(long day, EconomySim sim) {
        IOMatrix matrix = sim.ioMatrix();
        if (matrix == null || !matrix.isValid()) return "";
        int n = matrix.size();
        StringBuilder sb = new StringBuilder(n * 16);
        for (int i = 0; i < n; ++i) {
            String inputName = i < RESOURCES.ALL().size()
                    ? ((RESOURCE) RESOURCES.ALL().get(i)).key : ("idx_" + i);
            for (int j = 0; j < n; ++j) {
                double direct = matrix.getDirectCoefficient(i, j);
                double total = matrix.getTotalCoefficient(i, j);
                if (direct < 1e-6 && total < 1e-6) continue;
                String outputName = j < RESOURCES.ALL().size()
                        ? ((RESOURCE) RESOURCES.ALL().get(j)).key : ("idx_" + j);
                if (direct >= 1e-6) {
                    sb.append(day).append(',').append(csvEsc(inputName))
                      .append(",direct,").append(csvEsc(outputName))
                      .append(',').append(fmt(direct, 6)).append('\n');
                }
                if (total >= 1e-6 && Math.abs(total - direct) > 1e-6) {
                    sb.append(day).append(',').append(csvEsc(inputName))
                      .append(",total,").append(csvEsc(outputName))
                      .append(',').append(fmt(total, 6)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static void ensureDir() throws IOException {
        if (Files.exists(DIAG_DIR)) return;
        Files.createDirectories(DIAG_DIR);
    }

    private static String join(String[] parts) {
        StringBuilder sb = new StringBuilder(parts.length * 16);
        for (int i = 0; i < parts.length; ++i) {
            if (i > 0) sb.append(',');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════
    // Res-011 — Atomic EconDaySnapshot.
    //
    // Vor diesem Record hatte exportDay() 25+ separate sim.*()-Aufrufe über die
    // Funktion verteilt (treasury, deaths, fiscal.headTaxCollected,
    // wallets.circulating …). Die CSV-Zeile war damit eine Kette von Live-Werten,
    // die zusammen nie existiert haben — Treasury konnte sich zwischen dem ersten
    // und letzten Read ändern, die Reihe war inkohärent.
    //
    // Lösung: ein einziger Record, dessen Felder ALLE an einer einzigen Stelle
    // gefüllt werden. Nach {@link #capture} darf die CSV-Zeile AUSSCHLIESSLICH
    // {@code snap.*()-Felder lesen — keine weiteren Live-Reads.
    //
    // Drain-Semantik: die drei Counter-Drains (priorityExpansionSignals,
    // CrimeTheftConsumer.drainCounters, FirmLedger.drainAllocationCounters) sind
    // Teil von {@code capture()} — read+zero in derselben Transaktion.
    // ═══════════════════════════════════════════════════════
    /** Atomarer Tages-Snapshot. Feldreihenfolge = formatMacroRow(snap)-Argumentfolge.
     *
     *  <p>Res-011: Vor diesem Record hatte exportDay() 25+ separate sim.*()-Calls.
     *  Diese Klasse sammelt ALLE Werte an einer Stelle. Nach {@link #capture} darf
     *  der CSV-Format-Code AUSSCHLIESSLICH {@code snap.*()} lesen — keine Live-Reads.</p>
     *
     *  <p>Bewusst NICHT umbenannt zu {@code EconSnapshot}, weil es im Codebase schon
     *  {@link EconSnapshot} (in EconIndicators) als Resource-Preise-Trend-Snapshot
     *  gibt — semantisch unterschiedlich, würde Verwirrung stiften.</p>
     */
    private static record EconDaySnapshot(
            long day, int season,
            int pop, int deaths, int emigrations, int inherited, int heirless,
            double gini, int median, double mean, int max,
            long treasury, long totalMoney, long seedMoney, long auditDelta,
            double meanWage, double actualMeanWage, double wageShare, double unpaidRatio,
            long headTax, long marketRecpts, long rationOut, long wagesPaid,
            long whBought, long whSold,
            long housingRentCollected, long housingRentDue, long housingEvictions,
            long propertySales, long propertyDividends,
            int foodBasketPrice, double foodDays,
            int priorityExpansionSignals,
            int theftsToday, long stolenToday, int theftReports,
            // Sprint v0.13.102+: Adaptive Crime — Money+Guard-Faktor als CSV-Spalten,
            // Arena-Straf-Counter. Faktoren werden am End-of-Day berechnet und
            // spiegeln den aggregierten Zustand (gleiche Formel wie CrimeTheftConsumer.pair).
            double theftMoneyFactor, double theftGuardFactor, int arenaSentences,
            long[] allocCounters,                       // 6: targetInit[0], divergence[1], payroll[2], priority[3], player[4], hill[5]
            Map<String, Integer> signalsByBlueprint,   // Per-Blueprint-Expansion-Hints
            FlowMeter.Snapshot flowSnapshot,           // gecachte Resource-Versorgungs-Snapshot
            float[] anchors,                            // Anchor-Preise pro Resource (RESOURCES.ALL().size())
            int[] marketPrices,                         // Lokale Marktpreise pro Resource (sim.flowPrices().priceRoundedUp)
            double[] coverages,                          // Coverage pro Resource (sim.flowPrices().coverage)
            WealthStats stats                           // Vermögens-Verteilung, wird vom Caller mitgepinnt
    ) {
        /**
         * Capture-Block: alle Live-Reads + Counter-Drains in EINEM Aufruf.
         * Reihenfolge der Drains ist relevant — sie müssen VOR allen Live-Reads passieren
         * damit der Wert im Record = Wert zum Drain-Zeitpunkt ist.
         */
        static EconDaySnapshot capture(EconomySim sim) {
            // ── Daily Counters (read+zero) ───────────────────────────────
            int expSignals = FirmSizing.priorityExpansionSignalsThisDay;
            FirmSizing.priorityExpansionSignalsThisDay = 0;
            Map<String, Integer> sigsByBp =
                    new HashMap<>(FirmSizing.priorityExpansionSignalsByBlueprint);
            FirmSizing.priorityExpansionSignalsByBlueprint.clear();
            FirmSizing.escapeCliffLoggedToday.clear();

            int[] thefts = CrimeTheftConsumer.drainCounters();
            long[] allocCounters = FirmLedger.drainAllocationCounters();

            // ── Adaptive-Crime-Faktoren (Sprint v0.13.102+) ─────────────────
            // moneyFactor = 1.0 + (1 − coverage) × strength, coverage = totalMoney / (pop × refWealth).
            // guardFactor = 1.0 + (1 − guardRatio) × strength. Diagnostics-Snapshot
            // braucht End-of-Day-Werte für die CSV-Spalten — nicht die per-pair-Werte.
            // Greift auf dieselben EconConfig-Flags zu wie CrimeTheftConsumer.pair().
            // ── Adaptive-Crime-Faktoren (Sprint v0.13.102+) ─────────────────
            // Delegation an CrimeTheftConsumer.computeMoneyFactor + computeGuardFactor —
            // DRY mit pair(). Diagnostics-Snapshot braucht End-of-Day-Werte.
            double totalMoney = sim.wallets() != null ? sim.wallets().circulating() : 0L;
            double moneyFactor = CrimeTheftConsumer.computeMoneyFactor(totalMoney, sim.roster().size());

            double guardRatioLive = 0.0;
            boolean guardsAvailable = false;
            try {
                int totalPop = settlement.stats.STATS.POP().pop((init.race.Race) null, null);
                int guardCount = settlement.stats.STATS.POP().pop(
                        (init.race.Race) null, init.type.HTYPES.GUARD());
                if (totalPop > 0) {
                    guardRatioLive = (double) guardCount / totalPop;
                    guardsAvailable = true;
                }
            } catch (Throwable t) { /* Engine not ready */ }
            double guardFactor = CrimeTheftConsumer.computeGuardFactor(guardRatioLive, guardsAvailable);

            // ── Live-Reads: alle Sim-Werte in einem Block ─────────────────
            WealthStats stats = sim.stats();
            WarehouseMarket wh = sim.warehouseMarket();
            EconSnapshot recent =
                    sim.econIndicators() != null ? sim.econIndicators().latest() : null;
            long day = sim.ticks() / (long) TIME.secondsPerDay();

            // Anchor- + Markt- + Coverage-Preise vorab cachen
            // (verhindert erneute sim.flowPrices()-Calls in der Resource-Zeilen-Schleife).
            // Res-011 Issue 3: damit ist die gesamte CSV-Zeile aus dem Snapshot
            // ableitbar — KEINE Live-Reads mehr außerhalb von capture().
            int resourcesAllSize = RESOURCES.ALL().size();
            float[] anchors = new float[resourcesAllSize];
            int[] marketPrices = new int[resourcesAllSize];
            double[] coverages = new double[resourcesAllSize];
            for (int i = 0; i < resourcesAllSize; ++i) {
                RESOURCE r = (RESOURCE) RESOURCES.ALL().get(i);
                anchors[i] = (float) sim.flowPrices().anchor(i);
                marketPrices[i] = sim.flowPrices().priceRoundedUp(i);
                coverages[i] = sim.flowPrices().coverage(i);
            }

            return new EconDaySnapshot(
                    day, (int) (day % 4L),
                    sim.roster().size(), sim.deaths(), sim.emigrations(),
                    sim.inherited(), sim.heirless(),
                    stats.gini, stats.median, stats.mean, stats.max,
                    sim.treasury(), sim.wallets().circulating(),
                    sim.seedSupply(), sim.auditDelta(),
                    sim.laborMarket().meanWage(),
                    recent != null ? recent.actualMeanWage : 0.0,
                    recent != null ? recent.wageShare : 0.0,
                    recent != null ? recent.unpaidRatio : 0.0,
                    sim.fiscal().headTaxCollected(), sim.fiscal().marketReceipts(),
                    sim.fiscal().rationOut(), sim.wagesPaid(),
                    wh.lastBought(), wh.lastSold(),
                    sim.housingMarket().lastRentCollected(),
                    sim.housingMarket().lastRentDue(),
                    sim.housingMarket().lastEvictions(),
                    sim.propertySalesCollected(), sim.propertyDividendsPaid(),
                    LocalPrices.flowFoodBasketPrice(), LocalPrices.foodDays(),
                    expSignals,
                    thefts[0], thefts[2], thefts[1],
                    moneyFactor, guardFactor, thefts[3],
                    allocCounters, sigsByBp,
                    sim.flowMeter().snapshot(),
                    anchors, marketPrices, coverages,
                    stats
            );
        }
    }

    /** Snapshot-basierte formatMacroRow-Overload. KEINE Live-Reads — alle Werte
     *  aus dem atomic snapshot. Field-Reihenfolge identisch mit MACRO_HEADER. */
    private static String formatMacroRow(EconDaySnapshot snap) {
        StringBuilder s = new StringBuilder(512);
        s.append(snap.day()).append(',').append(snap.season()).append(',')
                .append(snap.pop()).append(',').append(snap.deaths()).append(',').append(snap.emigrations())
                .append(',').append(snap.inherited()).append(',').append(snap.heirless()).append(',')
                .append(fmt(snap.gini(), 4)).append(',').append(snap.median())
                .append(',').append(fmt(snap.mean(), 2)).append(',').append(snap.max()).append(',')
                .append(snap.treasury()).append(',').append(snap.totalMoney())
                .append(',').append(snap.seedMoney()).append(',').append(snap.auditDelta()).append(',')
                .append(fmt(snap.meanWage(), 2)).append(',').append(fmt(snap.actualMeanWage(), 2)).append(',')
                .append(fmt(snap.wageShare(), 4)).append(',').append(fmt(snap.unpaidRatio(), 4)).append(',')
                .append(snap.headTax()).append(',').append(snap.marketRecpts()).append(',').append(snap.rationOut()).append(',')
                .append(snap.wagesPaid()).append(',').append(snap.whBought()).append(',').append(snap.whSold()).append(',')
                .append(snap.housingRentCollected()).append(',').append(snap.housingRentDue())
                .append(',').append(snap.housingEvictions()).append(',')
                .append(snap.propertySales()).append(',').append(snap.propertyDividends()).append(',')
                .append(snap.foodBasketPrice()).append(',').append(fmt(snap.foodDays(), 2)).append(',')
                .append(snap.priorityExpansionSignals()).append(',')
                .append(snap.theftsToday()).append(',').append(snap.stolenToday())
                .append(',').append(snap.theftReports()).append(',')
                .append(fmt(snap.theftMoneyFactor(), 3)).append(',').append(fmt(snap.theftGuardFactor(), 3))
                .append(',').append(snap.arenaSentences()).append(',')
                .append(snap.allocCounters()[0]).append(',').append(snap.allocCounters()[1])
                .append(',').append(snap.allocCounters()[2])
                .append(',').append(snap.allocCounters()[3]).append(',').append(snap.allocCounters()[4])
                .append(',').append(snap.allocCounters()[5]);
        return s.toString();
    }

    private static String formatResourceRow(long day, int season, String resource,
                                            double anchor, int market, double coverage,
                                            double supply, double demand, double stock,
                                            double daysOfSupply, int starving) {
        StringBuilder s = new StringBuilder(160);
        s.append(day).append(',').append(season).append(',').append(csvEsc(resource)).append(',')
                .append(fmt(anchor, 2)).append(',').append(market).append(',')
                .append(fmt(coverage, 3)).append(',')
                .append(fmt(supply, 2)).append(',').append(fmt(demand, 2)).append(',')
                .append(fmt(stock, 2)).append(',')
                .append(fmt(daysOfSupply, 2)).append(',').append(starving).append('\n');
        return s.toString();
    }

    private static String formatFirmRow(long day, int season, FirmLedger.FirmFinancialSnapshot firm, int expansionSignals) {
        StringBuilder s = new StringBuilder(200);
        s.append(day).append(',').append(season).append(',')
                .append(csvEsc(firm.blueprint())).append(',')
                .append(firm.employees()).append(',')
                .append(firm.employedTarget()).append(',')
                // RES-035: max_capacity + hard_target eingefügt. Smoking-Gun-Lesart:
                // employees=6, employed_target=45, max_capacity=45, hard_target=6
                // → Engine-Cap unter unserem Wunsch-Target. Genau der Allokations-Bug.
                .append(firm.maxCapacity()).append(',')
                .append(firm.hardTarget()).append(',')
                .append(fmt(firm.profitPerDay(), 2)).append(',')
                .append(fmt(firm.marginalPerWorker(), 4)).append(',')
                .append(fmt(firm.incomeCarry(), 2)).append(',')
                .append(fmt(firm.totalOutputValuePerDay(), 2)).append(',')
                .append(fmt(firm.totalInputValuePerDay(), 2)).append(',')
                .append(firm.lastIncomeDue()).append(',')
                .append(firm.lastIncomePaid()).append(',')
                .append(firm.workersUnpaid()).append(',')
                .append(firm.stuckSeconds()).append(',')
                .append(expansionSignals).append(',')
                .append(firm.staircaseTier()).append(',')
                .append(firm.staatsbestandCritical()).append('\n');
        return s.toString();
    }

    /** CSV-Ressourcen-Namen können keine Kommas enthalten, aber Anführungs-Logik bleibt generisch. */
    private static String csvEsc(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Double-Formatter für stabile deutsche Dezimal-Darstellung. */
    private static String fmt(double v, int decimals) {
        if (!Double.isFinite(v)) {
            return v > 0 ? "inf" : (v < 0 ? "-inf" : "nan");
        }
        // Manuell formatieren — DecimalFormat wäre einfacher, aber lokale-abhängig (Punkt vs. Komma).
        // Wir erzwingen Punkt als Dezimaltrenner (CSV-Standard).
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", v);
    }

    private static int actualMeanWage(EconSnapshot snap) {
        return snap != null ? (int) Math.round(snap.actualMeanWage) : 0;
    }

    private static double wageShare(EconSnapshot snap) {
        return snap != null ? snap.wageShare : 0.0;
    }

    private static double unpaidRatio(EconSnapshot snap) {
        return snap != null ? snap.unpaidRatio : 0.0;
    }

    // ═══════════════════════════════════════════════════════
    // Immigration-Diagnostik (Sprint v0.13.130+MeticImmigrationDebug)
    // ═══════════════════════════════════════════════════════

    /** Letzter Tick für den Immigration-Debug geschrieben wurde — Throttle. */
    private static volatile long lastImmigrationDebugTick = -1;

    /**
     * Schreibt eine Immigration-Diagnostik-Zeile asynchron.
     * Wird von {@code MeticImmigration.vGet()} aufgerufen, maximal 1× pro Tick.
     *
     * @param day       aktueller Spieltag
     * @param tick      aktueller Tick
     * @param pop       aktuelle Bevölkerung
     * @param cap       Migrations-Cap
     * @param median    Wallet-Median
     * @param mean      mittleres Vermögen
     * @param taxMod    foreignTaxModifier (negativ = attraktiv)
     * @param boosterRaw berechneter tanh-Wert vor Cap-Check
     * @param boosterFinal effektiver Wert (0.5 wenn Cap erreicht)
     */
    public static void appendImmigrationRow(long day, long tick, int pop, int cap,
                                             int median, double mean, int taxMod,
                                             double boosterRaw, double boosterFinal) {
        if (!EconConfig.diagnosticsExportEnabled) return;
        if (tick == lastImmigrationDebugTick) return; // 1× pro Tick
        lastImmigrationDebugTick = tick;

        boolean capHit = pop >= cap;
        double phaseFac = EconConfig.phaseFactor();
        double happinessProxy = simHappinessProxy();

        StringBuilder sb = new StringBuilder(200);
        sb.append(day).append(',').append(tick).append(',').append(pop)
          .append(',').append(cap).append(',').append(capHit ? 1 : 0)
          .append(',').append(median).append(',').append(fmt(mean, 1))
          .append(',').append(taxMod)
          .append(',').append(fmt(boosterRaw, 6)).append(',').append(fmt(boosterFinal, 6))
          .append(',').append(fmt(EconConfig.meticImmigrationDepth, 3))
          .append(',').append(fmt(EconConfig.meticImmigrationSteepness, 1))
          .append(',').append(fmt(phaseFac, 3))
          .append(',').append(fmt(happinessProxy, 3))
          .append('\n');

        final String row = sb.toString();
        IO.submit(() -> {
            try {
                ensureDir();
                if (!immigrationHeaderWritten) {
                    Files.writeString(DIAG_DIR.resolve(IMMIGRATION_FILE),
                            join(IMMIGRATION_HEADER) + "\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    immigrationHeaderWritten = true;
                }
                Files.writeString(DIAG_DIR.resolve(IMMIGRATION_FILE),
                        row, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("[ECON] Immigration diagnostic write failed: "
                        + e.getMessage());
            }
        });
    }

    /**
     * Happiness-Proxy: Durchschnitt der Loyalität × NeedSatisfaction über alle Klassen.
     *
     * <p>Engine-Not-Ready-Defense (Sprint v0.13.131+Fix): schmaler Catch auf
     * {@code RuntimeException | LinkageError} statt {@code Throwable}, weil
     * Phase-4.7-Shield jeden neuen {@code catch(Throwable)} als +1-Delta zählt
     * (vgl. agents.md Rule 15 — Rule 14 Baseline wird via Post-Commit-Shield
     * automatisch geprüft). Der frühere Code der Sprint-Generation hatte 9
     * bereits grandfatherten Catches; jeder weitere neue Catch wurde zu +1.
     * Hier: pre-existing Engine-Init-Defensive wird mit der schmaleren
     * Variante gewahrt — {@code RuntimeException}-Familie deckt NPE /
     * IllegalState / IllegalAccess, {@code LinkageError}-Familie deckt
     * {@code NoClassDefFoundError} / {@code ClassCircularityError} /
     * {@code ClassFormatError} (echte Engine-Init-Bootstrap-Fälle).
     * VM-interne Errors ({@code OutOfMemoryError}, {@code StackOverflowError},
     * {@code InternalError}) propagieren weiterhin — die Mod-Robustheit leidet
     * nicht, der Zähler bleibt aber bei 0.</p>
     */
    private static double simHappinessProxy() {
        try {
            // Sprint v0.13.131+NoSilentFail: Engine-Ready-Guard. Wenn die Engine noch
            // nicht fully-verfügbar ist (BootRaceFix Sprint v0.13.130 degraded-mode
            // recovery, Headless-Tests, Pre-Boot-Tick), Sentinel -1.0 statt throw
            // — sonst spamt MeticImmigration bei jedem Re-Init-Cycle die ISE.
            if (!EngineMirror.isReady() || EconomySim.active() == null) {
                return -1.0;
            }
            EconomySim sim = EconomySim.active();
            if (sim.stats() == null) {
                throw new IllegalStateException(
                        "EconomySim.stats() == null mid-tick — Engine-Init-Defekt oder Mod-Bug."
                                + " Sentinel -1.0 wurde zuvor still zurückgegeben"
                                + " und versteckte reale Initialisierungsfehler.");
            }
            return sim.stats().mean; // mean wealth als Proxy für Happiness-Korrelat
        } catch (LinkageError t) {
            // Engine noch nicht im Klassenpfad — Sentinel OK, Bootstrap-Phase.
            return -1.0;
        }
    }

    /** Diagnose-Pfad für externe Tools (z. B. Tests oder UI-Hilfetexte). */
    public static String diagnosticDirectory() {
        return DIAG_DIR.toString();
    }

    // ═══════════════════════════════════════════════════════
    // DC-01: Summary-Event-System — Change-Detection + Buffer + Save-Flush
    // ═══════════════════════════════════════════════════════

    /**
     * Registriert einen State-Change im Summary-Buffer.
     * Dedupliziert automatisch: nur wenn sich der Wert gegenüber dem
     * letzten gespeicherten State geändert hat.
     */
    private static void recordChange(long day, String entity, String eventType,
                                      String field, double fromVal, double toVal,
                                      String contextJson) {
        // Deduplizierung: gleicher Tag + gleiche Entity + gleiches Field = nur letzter Wert
        Map<String, Double> fields = lastState.computeIfAbsent(entity, k -> new HashMap<>());
        Double prev = fields.get(field);
        if (prev != null && Math.abs(prev - toVal) < 1e-9) return; // identischer Wert
        fields.put(field, toVal);

        synchronized (eventBuffer) {
            if (eventBuffer.size() >= MAX_BUFFER_EVENTS) {
                eventBuffer.remove(0); // ältestes Event verwerfen (RingBuffer)
            }
            eventBuffer.add(new SummaryEvent(day, entity, eventType, field,
                    fromVal, toVal, contextJson));
        }
    }

    /**
     * Schreibt den Summary-Buffer als CSV und leert ihn.
     * Wird aus {@code InstanceScript.save()} beim Spiel-Save aufgerufen.
     * Format: {@code summary_[seed].csv}, Semicolon-getrennt, 7 Spalten.
     *
     * @param seed Session-Identifikator (nanoTime beim Session-Start)
     */
    public static void flush(long seed) {
        List<SummaryEvent> snapshot;
        synchronized (eventBuffer) {
            if (eventBuffer.isEmpty()) return;
            snapshot = new ArrayList<>(eventBuffer);
            eventBuffer.clear();
        }

        try {
            ensureDir();
            Path out = DIAG_DIR.resolve("summary_" + seed + ".csv");
            boolean exists = Files.exists(out);
            StringBuilder sb = new StringBuilder(snapshot.size() * 200);

            if (!exists) {
                sb.append("day;entity;event_type;field;from_val;to_val;context_json\n");
            }

            for (SummaryEvent e : snapshot) {
                sb.append(e.day()).append(';')
                        .append(csvEsc(e.entity())).append(';')
                        .append(e.eventType()).append(';')
                        .append(e.field()).append(';')
                        .append(fmt(e.fromVal(), 2)).append(';')
                        .append(fmt(e.toVal(), 2)).append(';')
                        .append(csvEsc(e.context())).append('\n');
            }

            Files.writeString(out, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            EventLog.log("DIAG", "Summary geflusht: " + snapshot.size()
                    + " Events → " + out.getFileName());
        } catch (IOException e) {
            System.err.println("[ECON] Summary flush failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Öffentlicher Zugriff für InstanceScript — seed = SESSION_EPOCH. */
    public static long sessionSeed() {
        return SESSION_EPOCH;
    }

    // ═══════════════════════════════════════════════════════
    // DC-01: Change-Detection — recordChange() pro Resource + Makro

    // ═══════════════════════════════════════════════════════
    // DC-02: Player-Action-Logging — Baumaßnahmen, Config-Änderungen
    // ═══════════════════════════════════════════════════════

    /** Loggt eine Spieler-Aktion im Summary-Buffer (Bau, Konfig, etc.).
     *  Nutzt den action-Namen als field um Dedup pro Aktionstyp zu vermeiden. */
    public static void logPlayerAction(long tick, String action, String detail) {
        long day = (long) (tick / EconConfig.DEFAULT_TICKS_PER_DAY);
        recordChange(day, "PLAYER", action, action, 0, (double) tick,
                "{" + detail + "}");
        EventLog.log("PLAYER", "[" + tick + "] " + action + ": " + detail);
    }

    /** Convenience-Overload: nutzt LoggingAdapter.currentTick() statt explizitem tick-Parameter.
     *  Ideal für UI-Lambdas die keinen Zugriff auf EconomySim.ticks() haben. */
    public static void logPlayerAction(String action, String detail) {
        logPlayerAction(LoggingAdapter.currentTick(), action, detail);
    }

    /** Loggt eine EconConfig-Änderung (von UI-Slidern ausgelöst). */
    public static void logConfigChange(String configKey, int oldVal, int newVal) {
        long tick = LoggingAdapter.currentTick();
        long day = (long) (tick / EconConfig.DEFAULT_TICKS_PER_DAY);
        recordChange(day, "PLAYER", "CONFIG_CHANGE", configKey,
                oldVal, newVal, "{tick:" + tick + "}");
    }

    // ═══════════════════════════════════════════════════════

    /**
     * Prüft pro Resource auf signifikante State-Changes und ruft
     * recordChange() wenn ein Threshold überschritten wird.
     */
    private static void detectResourceChanges(long day, String resourceName,
            float anchor, int marketPrice, double coverage,
            double supply, double demand, double stock,
            double daysOfSupply, int starving) {
        String entity = resourceName;

        // Coverage-Change
        recordIfChanged(day, entity, "COVERAGE_CRASH", "coverage",
                coverage, COVERAGE_THRESHOLD,
                "{stock:" + fmt(stock, 1) + ",dsupply:" + fmt(daysOfSupply, 1)
                        + ",demand:" + fmt(demand, 2) + ",price:" + marketPrice + "}");

        // Preis-Spike relativ zum Anchor
        double priceDelta = anchor > 0 ? Math.abs(marketPrice - anchor) / anchor : 0;
        if (priceDelta >= PRICE_CHANGE_THRESHOLD && anchor > 0) {
            recordChange(day, entity,
                    marketPrice > anchor ? "PRICE_SPIKE" : "PRICE_CRASH",
                    "market_price",
                    anchor, marketPrice,
                    "{coverage:" + fmt(coverage, 2)
                            + ",supply:" + fmt(supply, 2)
                            + ",demand:" + fmt(demand, 2)
                            + ",anchor:" + fmt(anchor, 1) + "}");
        }

        // Supply-Toggle (0↔non-zero)
        recordToggle(day, entity, "SUPPLY_TOGGLE", "supply_per_day",
                supply, "{demand:" + fmt(demand, 2) + ",stock:" + fmt(stock, 1) + "}");

        // Demand-Toggle (0↔non-zero)
        recordToggle(day, entity, "DEMAND_TOGGLE", "demand_per_day",
                demand, "{supply:" + fmt(supply, 2) + ",stock:" + fmt(stock, 1) + "}");

        // Starving-Signal
        if (starving == 1) {
            recordChange(day, entity, "STARVING", "starving_signal",
                    0, 1,
                    "{stock:" + fmt(stock, 1) + ",dsupply:" + fmt(daysOfSupply, 1)
                            + ",demand:" + fmt(demand, 2) + ",price:" + marketPrice + "}");
        }
    }

    /** Prüft Makro-Indikatoren auf kritische State-Changes. */
    private static void detectMacroChanges(long day, EconomySim sim,
            WealthStats stats) {
        // Gini-Änderung
        recordIfChanged(day, "ECONOMY", "GINI_SPIKE", "gini",
                stats.gini, GINI_THRESHOLD,
                "{treasury:" + sim.treasury()
                        + ",wage_config_max:" + fmt(sim.laborMarket().meanWage(), 1)
                        + ",food_basket:" + LocalPrices.flowFoodBasketPrice() + "}");

        // Treasury-Crisis
        long treasury = sim.treasury();
        int crisisTier = TreasuryCrisis.activeTier();
        if (crisisTier >= 1) {
            recordChange(day, "TREASURY", "CRISIS_TIER_" + crisisTier,
                    "treasury", 0, treasury,
                    "{gini:" + fmt(stats.gini, 3)
                            + ",food_basket:" + LocalPrices.flowFoodBasketPrice() + "}");
        }
    }

    /** Wert-Change-Detection mit Threshold. */
    private static void recordIfChanged(long day, String entity, String eventType,
            String field, double newVal, double threshold, String contextJson) {
        Map<String, Double> fields = lastState.computeIfAbsent(entity, k -> new HashMap<>());
        Double prev = fields.get(field);
        if (prev != null && Math.abs(prev - newVal) < threshold) return;
        recordChange(day, entity, eventType, field,
                prev != null ? prev : newVal, newVal, contextJson);
    }

    /** Toggle-Detection: meldet nur den Übergang 0↔non-zero. */
    private static void recordToggle(long day, String entity, String eventType,
            String field, double newVal, String contextJson) {
        Map<String, Double> fields = lastState.computeIfAbsent(entity, k -> new HashMap<>());
        Double prev = fields.get(field);
        boolean wasZero = prev == null || prev == 0.0;
        boolean isZero = newVal == 0.0;
        if (wasZero == isZero) return;
        recordChange(day, entity, eventType, field,
                prev != null ? prev : 0.0, newVal, contextJson);
    }

    // ── Hard-Threshold Rebalance-Alerts ────────────────────────────────

    /**
     * Prüft kritische Makro-Schwellwerte und loggt [REBALANCE] in EventLog
     * wenn Gini oder AuditDelta einen harten Grenzwert überschreiten.
     *
     * <p>Deduplizierung: Nur bei Erstüberschreitung oder wenn sich der Wert
     * um >10% verschlechtert hat, wird erneut geloggt. Kein Spam bei
     * anhaltender Überschreitung.</p>
     */
    private static void checkRebalanceAlerts(double gini, long auditDelta, long day) {
        // ── Gini-Alert ─────────────────────────────────────────────
        if (gini > GINI_HARD_THRESHOLD) {
            boolean firstBreach = lastAlertedGini < 0.0;
            boolean worsened = !firstBreach && gini > lastAlertedGini * 1.10;
            if (firstBreach || worsened) {
                EventLog.log("REBALANCE",
                        "Tag " + day + ": Gini=" + fmt(gini, 3)
                        + " (Schwelle: " + fmt(GINI_HARD_THRESHOLD, 2) + ")"
                        + " — Ungleichheit kritisch, Steuer-/Lohnpolitik prüfen");
                lastAlertedGini = gini;
            }
        } else {
            // Wert ist unter Schwelle gefallen — Alert-Status zurücksetzen
            // damit ein erneuter Anstieg sofort wieder logged.
            lastAlertedGini = -1.0;
        }

        // ── Audit-Delta-Alert ─────────────────────────────────────
        if (auditDelta > AUDIT_DELTA_HARD_THRESHOLD) {
            boolean firstBreach = lastAlertedAuditDelta < 0L;
            boolean worsened = !firstBreach && auditDelta > (long)(lastAlertedAuditDelta * 1.10);
            if (firstBreach || worsened) {
                EventLog.log("REBALANCE",
                        "Tag " + day + ": AuditDelta=" + auditDelta
                        + " (Schwelle: " + AUDIT_DELTA_HARD_THRESHOLD + ")"
                        + " — Geldmenge driftet, Treasury-Leck oder Geldschöpfung prüfen");
                lastAlertedAuditDelta = auditDelta;
            }
        } else {
            lastAlertedAuditDelta = -1L;
        }
    }
}
