package vannon.syx.economy.core;

import java.util.function.Consumer;

/**
 * Verhindert Re-Entry in kritische Update-Sektionen (Tick-Loops, Save/Load-Hooks,
 * Engine-Mirror-Bridges die von Vanilla zweimal angesprochen werden können;
 * pre-v0.13.119 war dies `EngineSeams`-Klasse, die via statische Methoden
 * direkt mit der Engine sprach — seither über `EngineMirror.api().<sub>().<method>()`).
 *
 * <p>Anwendungs-Pattern (try-with-style, manuell):
 * <pre>{@code
 * private final ReentryGuard guard = new ReentryGuard("Subsystem.update()");
 *
 * public void update() {
 *     if (!guard.tryEnter()) return;       // re-entry: skip (one-shot log)
 *     try {
 *         // ...do work...
 *     } finally {
 *         guard.exit();                    // garantierter Reset auch bei RuntimeException
 *     }
 * }
 * }</pre>
 *
 * <p>Semantik: <b>per-instance</b> one-shot-Warning. Wenn zwei Subsysteme gleichzeitig
 * Re-Entry sehen, warnt jedes einmal — nicht „erstes Subsystem schweigt das zweite".
 *
 * <p>Thread-Safety: Felder sind {@code volatile}. Defensive Maßnahme gegen Vanilla-V72+
 * Thread-Wechsel (Audio-Thread, Save-Thread). Im aktuellen Setup single-threaded;
 * Memory-Barriers kosten hier nichts messbar.
 *
 * <p>Design ist Domain-agnostisch: kein hardcoded Log-Tag (frühere "[ECON]"-Variante
 * war falsch weil Klassen-Wiederverwendung in FirmLedger o.ä. dann "[ECON] FirmLedger..."
 * loggte). Log-Sink ist via {@link Consumer} injizierbar für Tests und
 * Logging-Policy-Routing (z.B. EventLog statt stderr).
 *
 * <p>Speicher-Overhead: 4 Felder (label + logSink + flag + warnFlag) pro Instanz.
 */
class ReentryGuard {

    private final String label;
    private final Consumer<String> logSink;
    private volatile boolean inProgress = false;
    private volatile boolean hasWarned = false;

    /**
     * Konstruktor mit Default-Log-Sink ({@code System.err::println}).
     *
     * @param label Menschen-lesbarer Name der geschützten Sektion. Erscheint in der
     *              one-shot Log-Zeile — Domain-Agnostisch, kein Tag-Prefix.
     */
    ReentryGuard(String label) {
        this(label, System.err::println);
    }

    /**
     * Konstruktor mit injizierbarem Log-Sink (für Tests und Logging-Policies).
     *
     * @param label   s. {@link #ReentryGuard(String)}
     * @param logSink Consumer der die one-shot Re-Entry-Warnung empfängt.
     *                {@code null} wird als {@code System.err::println} interpretiert
     *                (defensive Default, ähnlich Optional-Behandlung).
     */
    ReentryGuard(String label, Consumer<String> logSink) {
        this.label = label;
        this.logSink = logSink != null ? logSink : System.err::println;
    }

    /**
     * Versucht in die geschützte Sektion einzutreten. Returnt {@code true} wenn
     * erfolgreich (kein aktiver Re-Entry). Returnt {@code false} wenn die
     * Sektion bereits aktiv ist — der Caller MUSS dann sofort returnen.
     *
     * <p>Beim ersten Re-Entry wird eine einzelne Warning an den Log-Sink
     * emittiert; weitere Re-Entrys sind stumm. Loggt nicht in den Happy-Path.
     */
    boolean tryEnter() {
        if (this.inProgress) {
            if (!this.hasWarned) {
                this.logSink.accept(this.label + " re-entry detected — skipping (one-shot log).");
                this.hasWarned = true;
            }
            return false;
        }
        this.inProgress = true;
        return true;
    }

    /**
     * Verlässt die geschützte Sektion. Plain-Reset: kein Balancing-Check für
     * doppelten Aufruf — try/finally-Konvention ist robust genug, und ein
     * versehentlicher Doppel-Aufruf würde eh nur die nächste tryEnter() früher
     * durchwinken.
     */
    void exit() {
        this.inProgress = false;
    }
}
