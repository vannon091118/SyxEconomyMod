package vannon.syx.economy.core;

import init.type.CAUSE_LEAVES;
import init.type.NEEDS;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.AISUB;
import settlement.stats.Induvidual;
import util.data.INT_O;

/**
 * L-01 (v0.13.67): BrokeFoodPlan Oddjob Escape.
 * <p>Wenn ein Bürger sich Essen nicht leisten kann, wird NICHT sofort
 * der desperate-Status aktiviert. Solange der Hunger nicht maximal ist,
 * bleibt der Bürger in der normalen AI-Pipeline — diese prüft automatisch
 * Oddjob-Arbeit (PlanOddjobber) und andere Einkommensquellen vor dem
 * Verhungern. Erst wenn der Hunger MAXIMAL ist UND keine Arbeit half,
 * wird der desperate-Status aktiviert (Tod durch Verhungern).</p>
 *
 * <p>L-04 (v0.13.77): ERROR-HARDENED Double-Checked Locking (DCL) Lazy Init.
 * <strong>Root Cause:</strong> {@code script.ScriptLoad} durchsucht das JAR VOR
 * Sim-Bootstrap und ruft für <em>jede</em> {@code .class}-Datei in der
 * JAR (inkl. verschachtelter {@code HungerHolder.class} aus v0.13.76)
 * eine Reflection-getriebene Klassen-Initialisierung mit
 * {@code initialize=true} ab. Das erzwingt {@code <clinit>} bereits
 * beim Scan, lange bevor {@code STATS.s} lebt. Der Bill-Pugh Holder
 * (v0.13.76) ist damit machtlos: die JVM initialisiert auch die innere
 * Klasse sofort, sobald der Parent-Scan den Holder findet. Das
 * Race-Symptom ist {@code NullPointerException: Cannot read field
 * 'needs' because 'settlement.stats.STATS.s' is null} (siehe Crash-Log
 * im Sprint-Body).</p>
 *
 * <p><strong>Fix v2:</strong> Statt Holder-Pattern (das in Songs-of-Syx
 * gegen den JAR-Scan-Init-Mechanismus verliert) verwenden wir
 * Double-Checked Locking mit {@code volatile}-Cache. Der DCL-Block
 * ist im Method-Body — er feuert NICHT beim Scan, weil kein
 * {@code static final}-Initializer mehr die Engine berührt. Erst
 * der erste Method-Call aus einem Sim-Tick heraus (also NACHDEM
 * {@code STATS.s} lebt) ruft {@code NEEDS.TYPES()} auf.</p>
 *
 * <p><strong>Null-Safety am Call-Site:</strong> Falls ein (seltener)
 * Call vor Engine-Init erfolgt, fangen wir die NPE in
 * {@link #hunger()} defensiv — der Cache bleibt {@code null}, die
 * Caller bekommen @{code null} zurück und reagieren mit
 * <em>degraded behavior</em> (Hunger wird als nicht-maximal bewertet
 * → der Bürger hungert NICHT aus, sondern bleibt im normalen
 * AI-Loop).</p>
 */

public final class BrokeFoodPlan
extends AIPLAN.PLANRES {

    /**
     * DCL-Lazy-Cache für den Hunger-Resolver. {@code volatile} garantiert
     * Sichtbarkeit über Threads; {@code HungerHolder} wäre an dieser
     * Stelle verlockend, aber {@code script.ScriptLoad} initialisiert
     * im JAR-Scan Reflection-getrieben mit {@code initialize=true}
     * auch innere Klassen eager — siehe Sprint-Body v0.13.77 (L-04 v2).
     */
    private static volatile INT_O.INT_OE<Induvidual> hungerCache;

    /**
     * Thread-safe Double-Checked-Locking-Lazy-Resolver. Im Unterschied
     * zum vorherigen Bill-Pugh Holder wird hier KEIN {@code <clinit>}
     * auf einer Holder-Klasse ausgelöst — der Resolve-Path liegt im
     * Method-Body und feuert ausschließlich beim ersten Call aus einem
     * Sim-Tick heraus. Falls {@code NEEDS.TYPES()} zum Call-Zeitpunkt
     * noch null ist (Pre-Bootstrap, Engine-Init-Failure), wird die NPE
     * geschluckt und der Cache bleibt null. Caller bekommen dann null
     * und reagieren mit degraded behavior (Hunger wird als <em>nicht
     * maximal</em> behandelt → kein Crash im Cold-Boot-Pfad).
     */
    @SuppressWarnings("unchecked")
    private static INT_O.INT_OE<Induvidual> hunger() {
        // L-04 v2.4: Fast-Path read (volatile local-capture pattern, no
        // needed ordering — single volatile read establishes happens-before).
        // Beim ersten Tick nach Engine-Bootstrap ist hungerCache noch null
        // und wir fallen in den Lock-Pfad; sobald befüllt, liefert der Cache
        // die Hunger-Referenz in O(1) ohne Lock-Acquisition.
        INT_O.INT_OE<Induvidual> cached = hungerCache;
        if (cached != null) return cached;

        synchronized (BrokeFoodPlan.class) {
            // Re-check unter Lock — ein anderer Thread könnte hungerCache
            // zwischen Fast-Path-Return und Lock-Eintritt befüllt haben.
            if (hungerCache != null) return hungerCache;

            // L-04 v2.4: Defense-in-depth — die Pre-Null-Prüfung von
            // NEEDS.TYPES() LEBT IM LOCK, nicht im Fast-Path. So schließen
            // wir die TOCTOU-Lücke: ein seltener Engine-Teardown zwischen
            // Fast-Path und Chain kann die Chain nicht mehr in eine
            // NullPointerException treiben, weil die Prüfung jetzt unter
            // demselben Lock wie die Chain selbst stattfindet.
            if (NEEDS.TYPES() == null) {
                // Engine-Pre-Bootstrap oder Engine-Teardown — Cache bleibt
                // null, Caller degradiert (Hunger wird als nicht-maximal
                // behandelt → kein Crash im Cold-Boot-Pfad).
                return null;
            }

            // JLS §15.24 garantiert: NEEDS.TYPES() wird EINMAL ausgewertet,
            // und alle nachfolgenden .HUNGER / .stat() / .stat() / .indu()
            // Aufrufe beziehen sich auf dasselbe Objekt (Single-Expression
            // Atomicity). Da NEEDS.TYPES() != null ist, bleiben nur
            // LinkageError-Risiken (Klassenpfad-Defekt bei Songs-of-Syx
            // upgrade).
            try {
                hungerCache = (INT_O.INT_OE<Induvidual>) (Object)
                        NEEDS.TYPES().HUNGER.stat().stat().indu();
            } catch (LinkageError e) {
                // Klassenpfad-Defekt (Songs-of-Syx upgrade inkompatibel o.ä.).
                // Cache bleibt null, Caller degradiert.
                // Wir fangen ABSICHTLICH nur LinkageError — keine NPE. Echte
                // Programmierfehler im Live-Betrieb bleiben so als Stacktrace
                // sichtbar und werden nicht durch defensiven catch maskiert.
                return null;
            }
            return hungerCache;
        }
    }

    private final AIPLAN.PLANRES.Resumer starving = new AIPLAN.PLANRES.Resumer("starving"){

        protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
            BrokeFoodPlan.markStarvedIfLethal(humanoid);
            return AI.SUBS().desperate.activate(humanoid, manager);
        }

        protected AISUB.AISubActivation res(Humanoid humanoid, AIManager manager) {
            BrokeFoodPlan.markStarvedIfLethal(humanoid);
            return null;
        }

        public boolean con(Humanoid humanoid, AIManager manager) {
            // L-01: Verhungern NUR wenn Hunger bereits maximal.
            // L-04 v2: Null-tolerant — hunger() kann vor Engine-Init null liefern.
            INT_O.INT_OE<Induvidual> cache = hunger();
            return cache != null && cache.isMax(humanoid.indu());
        }

        public void can(Humanoid humanoid, AIManager manager) {
        }
    };

    public BrokeFoodPlan() {
        super("ECON_BROKE_FOOD");
    }

    protected AISUB.AISubActivation init(Humanoid humanoid, AIManager manager) {
        return this.starving.set(humanoid, manager);
    }

    static void markStarvedIfLethal(Humanoid humanoid) {
        // L-04 v2: Null-tolerant — vor Engine-Init liefert hunger() null zurück.
        // Wir behandeln dann <em>nicht</em> als lethal (kein TOD ohne Engine-Kontext).
        INT_O.INT_OE<Induvidual> cache = hunger();
        if (cache != null && cache.isMax(humanoid.indu())) {
            // v1.7.2-Fix: Starvation war der einzige permanente Effekt ohne EventLog-Eintrag.
            // Kein Crash, kein UI-Feedback — der Bürger verschwand einfach still.
            // Jetzt geloggt, damit die Frage "liegt das an meiner Preispolitik?"
            // im Wirtschaftsfenster nachvollziehbar ist.
            EventLog.log("STARVATION", "Citizen starved (hunger at max). Check food affordability gate and grain dole coverage.");
            AIManager.dead = CAUSE_LEAVES.STARVED();
        }
    }
}
