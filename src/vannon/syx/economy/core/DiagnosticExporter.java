package vannon.syx.economy.core;

import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
