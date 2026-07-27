package vannon.syx.economy.core;

import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
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

    private static final String[] MACRO_HEADER = {
            "game_day", "season", "population", "deaths", "emigrations", "inherited", "heirless",
            "gini", "median_wealth", "mean_wealth", "max_wealth",
            "treasury", "total_money", "seed_money", "audit_delta",
            "mean_wage", "actual_mean_wage", "wage_share", "unpaid_ratio",
            "head_tax", "market_receipts", "ration_out", "wages_paid", "warehouse_bought", "warehouse_sold",
            // Housing-Spalten sind *_last_tick, weil HousingMarket.update() saisonal abrechnet.
            // Außerhalb des Saison-Ticks = 0. Für kumulierte Werte StateWarehouses-Felder pinnen.
            "housing_rent_last_tick", "housing_rent_due_last_tick", "housing_evictions_last_tick",
            "property_sales", "property_dividends",
            "food_basket_price", "food_days"
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
            "profit_per_day", "marginal_per_worker", "income_carry",
            "total_output_value_per_day", "total_input_value_per_day",
            "last_income_due", "last_income_paid", "workers_unpaid"
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

    /** Header-Flag: verhindert Doppel-Header bei append. */
    private static volatile boolean macroHeaderWritten = false;
    private static volatile boolean resourceHeaderWritten = false;
    private static volatile boolean firmsHeaderWritten = false;

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
        String macroRow;
        StringBuilder resourceRows;
        StringBuilder firmsRows;
        try {
            // Snapshot alles auf dem Main-Thread — FlowMeter.snapshot() klont bereits
            FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
            // latest() returnt null wenn noch kein Snapshot existiert → einfach akzeptieren.
            EconSnapshot snap = sim.econIndicators().latest();
            WealthStats stats = sim.stats();
            WarehouseMarket whMarket = sim.warehouseMarket();

            // Makro-Werte (primitive snapshots, danach nicht mehr von anderen Threads angefasst)
            macroRow = formatMacroRow(
                    day, season,
                    sim.roster().size(), sim.deaths(), sim.emigrations(), sim.inherited(), sim.heirless(),
                    stats.gini, stats.median, stats.mean, stats.max,
                    sim.treasury(), sim.wallets().circulating(), sim.seedSupply(), sim.auditDelta(),
                    sim.laborMarket().meanWage(), actualMeanWage(snap), wageShare(snap), unpaidRatio(snap),
                    sim.fiscal().headTaxCollected(), sim.fiscal().marketReceipts(),
                    sim.fiscal().rationOut(), sim.wagesPaid(),
                    whMarket.lastBought(), whMarket.lastSold(),
                    // HousingRent: per-Tick-Snapshot — season-tick zeigt reale Werte, sonst 0.
                    // Zusammen mit lastRentDue() zeigt das CSV Soll/Ist-Gap (Miet-Schulden-Krise).
                    sim.housingMarket().lastRentCollected(), sim.housingMarket().lastRentDue(),
                    sim.housingMarket().lastEvictions(),
                    sim.propertySalesCollected(), sim.propertyDividendsPaid(),
                    LocalPrices.flowFoodBasketPrice(), LocalPrices.foodDays()
            );

            float[] anchors = new float[RESOURCES.ALL().size()];
            for (int i = 0; i < anchors.length; ++i) {
                RESOURCE r = (RESOURCE) RESOURCES.ALL().get(i);
                anchors[i] = (float) sim.flowPrices().anchor(i);
            }

            resourceRows = new StringBuilder(1024);
            int resourceCount = flow.size();
            for (int i = 0; i < resourceCount; ++i) {
                if (i >= flow.size()) break;
                float anchor = i < anchors.length ? anchors[i] : 0f;
                int market = sim.flowPrices().priceRoundedUp(i);
                double coverage = sim.flowPrices().coverage(i);
                double supply = flow.supplyPerDay(i);
                double demand = flow.demandPerDay(i);
                double stock = flow.stock(i);
                // Sentinel -1.0 wenn kein Bedarf. Pandas: values >= 0 filtern.
                double daysOfSupply = demand <= 0.0 ? -1.0 : stock / demand;
                int starving = (demand > 0.0 && daysOfSupply < 3.0) ? 1 : 0;
                String resourceName = i < RESOURCES.ALL().size()
                        ? ((RESOURCE) RESOURCES.ALL().get(i)).key
                        : ("idx_" + i);
                resourceRows.append(formatResourceRow(day, season, resourceName,
                        anchor, market, coverage, supply, demand, stock, daysOfSupply, starving));

                // DC-01: Summary-Change-Detection pro Resource
                detectResourceChanges(day, resourceName, anchor, market, coverage,
                        supply, demand, stock, daysOfSupply, starving);
            }

            // ── Firmen-Daten ──────────────────────────────────────────
            firmsRows = new StringBuilder(1024);
            FirmLedger ledger = sim.firmLedger();
            if (ledger != null) {
                for (FirmLedger.FirmFinancialSnapshot firm : ledger.firmFinancialSnapshots()) {
                    firmsRows.append(formatFirmRow(day, season, firm));
                }
            }

            // ── Hard-Threshold Rebalance-Alerts ────────────────────────
            // Loggen [REBALANCE] in EventLog wenn kritische Schwellen
            // überschritten werden. Nur bei Erstüberschreitung oder
            // signifikanter Verschlechterung (>10%) — kein Spam.
            checkRebalanceAlerts(stats.gini, sim.auditDelta(), day);

            // DC-01: Macro-Summary-Change-Detection
            detectMacroChanges(day, sim, stats);
        } catch (RuntimeException t) {
            System.err.println("[ECON] DiagnosticExport snapshot failed for day " + day
                    + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return;
        }

        // Hintergrund-Thread übernimmt Disk-IO. Kein Capture von "this" oder Live-Objekten.
        final String macroRowFinal = macroRow;
        final StringBuilder resourceRowsFinal = resourceRows;
        final StringBuilder firmsRowsFinal = firmsRows;
        IO.submit(() -> writeAll(macroRowFinal, resourceRowsFinal.toString(), firmsRowsFinal.toString()));
        // Sprint 6.4 — Unified CSV-Bridge: parallel-write a meta-row to DebugCsv
        // fuer Konsolidierte Debug-Analyse. Additive only.
        LoggingAdapter.csvTrace(
                LoggingAdapter.Category.SNAPSHOT,
                LoggingAdapter.Subsystem.ECON,
                LoggingAdapter.Severity.INFO,
                "diag_export",
                String.valueOf(day),
                "macro=" + (macroRow != null && !macroRow.isEmpty() ? "1" : "0")
                        + " resources=" + resourceRows.length()
                        + " firms=" + firmsRows.length());
    }

    /**
     * Synchroner Schreib-Pfad auf Hintergrund-Thread. Bei JVM-Shutdown wird
     * nicht auf Beendigung gewartet (max. ein paar hundert Bytes im
     * Executor-Buffer, keine Daten-Korruption).
     */
    private static void writeAll(String macroRow, String resourceRows, String firmsRows) {
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
        } catch (IOException e) {
            // Disk voll? Read-only? Mod-Directory schreibgeschützt?
            // Wir loggen an stderr und versuchen es morgen erneut — gibt kein EventLog hier.
            System.err.println("[ECON] DiagnosticExport write failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
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

    private static String formatMacroRow(long day, int season, int pop, int deaths, int emig, int inherited, int heirless,
                                          double gini, int median, double mean, int max,
                                          long treasury, long totalMoney, long seedMoney, long auditDelta,
                                          double meanWage, double actualWage, double wageShare, double unpaidRatio,
                                          long headTax, long marketRecpts, long rationOut, long wagesPaid,
                                          long whBought, long whSold,
                                          long housingRentCollected, long housingRentDue, long housingEvictions,
                                          long propertySales, long propertyDiv,
                                          int foodBasket, double foodDays) {
        StringBuilder s = new StringBuilder(512);
        s.append(day).append(',').append(season).append(',')
                .append(pop).append(',').append(deaths).append(',').append(emig).append(',').append(inherited).append(',').append(heirless).append(',')
                .append(fmt(gini, 4)).append(',').append(median).append(',').append(fmt(mean, 2)).append(',').append(max).append(',')
                .append(treasury).append(',').append(totalMoney).append(',').append(seedMoney).append(',').append(auditDelta).append(',')
                .append(fmt(meanWage, 2)).append(',').append(fmt(actualWage, 2)).append(',')
                .append(fmt(wageShare, 4)).append(',').append(fmt(unpaidRatio, 4)).append(',')
                .append(headTax).append(',').append(marketRecpts).append(',').append(rationOut).append(',')
                .append(wagesPaid).append(',').append(whBought).append(',').append(whSold).append(',')
                .append(housingRentCollected).append(',').append(housingRentDue).append(',').append(housingEvictions).append(',')
                .append(propertySales).append(',').append(propertyDiv).append(',')
                .append(foodBasket).append(',').append(fmt(foodDays, 2));
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

    private static String formatFirmRow(long day, int season, FirmLedger.FirmFinancialSnapshot firm) {
        StringBuilder s = new StringBuilder(200);
        s.append(day).append(',').append(season).append(',')
                .append(csvEsc(firm.blueprint())).append(',')
                .append(firm.employees()).append(',')
                .append(firm.employedTarget()).append(',')
                .append(fmt(firm.profitPerDay(), 2)).append(',')
                .append(fmt(firm.marginalPerWorker(), 4)).append(',')
                .append(fmt(firm.incomeCarry(), 2)).append(',')
                .append(fmt(firm.totalOutputValuePerDay(), 2)).append(',')
                .append(fmt(firm.totalInputValuePerDay(), 2)).append(',')
                .append(firm.lastIncomeDue()).append(',')
                .append(firm.lastIncomePaid()).append(',')
                .append(firm.workersUnpaid()).append('\n');
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
                        + ",mean_wage:" + fmt(sim.laborMarket().meanWage(), 1)
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
