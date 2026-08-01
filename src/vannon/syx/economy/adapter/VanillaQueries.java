package vannon.syx.economy.adapter;

import settlement.entity.humanoid.Humanoid;
import vannon.syx.economy.core.LoggingAdapter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sprint v0.13.131+VanillaQueriesDedupe: Single-reflection vanilla data fetchers
 * für {@link IHumanoidAccess}. Ersetzt den v0.13.129 Doppel-Reflection-Pfad
 * (outer Class.forName + inner Class.forName als catch-Fallback) durch EINEN
 * Pfad mit robuster {@code robustSize()}/{@code asIterable()} Helper-Klasse.
 *
 * <p><b>Was geändert wurde (Refactor-Begründung):</b>
 * <ul>
 *   <li><b>Single Reflection:</b> V0.13.129 hatte im catch-Block ein zweites
 *       POP-Daten-Reflection als Fallback. Das doutete sich als NICHT-redundant
 *       heraus — es war ein zusätzlicher Pfad gegen {@code STATS.POP().data.get(null)}.
 *       In Songs-of-Syx V71.44 ist dieser Pfad aber instabil:
 *       {@code popData.get(null)} returnt nicht zwingend eine Collection,
 *       sondern manchmal eine Bitmap, deren {@code value}-Auflösung ein
 *       zusätzliches {@code size()}-Hop benötigt — der schlägt fehl, der Catch
 *       schluckt, und residentCount() returnt 0 stillschweigend.</li>
 *   <li><b>robustSize():</b> Vanilla-API returnt je nach Pfad {@code ArrayList},
 *       {@code HashSet}, sometimes also {@code Set.toArray()} Indirektionen,
 *       oder direkt {@code Humanoid[]}. Ein reflektiver
 *       {@code .size()} wirft NoSuchMethodException bei Array-Typen (Java
 *       arrays haben {@code .length}, nicht {@code .size()}). Der Helper
 *       deckt Collection/Map/Array/primitive-Array ab und macht den Fallback
 *       deterministisch (return 0 statt silent Reflection-Fail mit unklarer
 *       Ursache).</li>
 *   <li><b>asIterable():</b> forEachResident castede direkt auf {@code Iterable<?>}.
 *       Wenn humans ein Object[] ist → ClassCastException → Catch schluckt still.
 *       Der Helper konvertiert Arrays transparent via {@code Arrays.asList(...)}.
 *       <b>Hinweis:</b> Iterator-Synthese-Lambdas (primitive-Arrays) sind pro
 *       {@code asIterable()}-Call isoliert. <b>Idiom:</b> Single-Thread-Caller —
 *       paralleler Iterate über gleiche {@code humans}-Referenz wird nicht
 *       unterstützt (kein synchronized, kein ConcurrentModification-Schutz).</li>
 * </ul>
 *
 * <p><b>Architektur-Stand v0.13.131:</b> VanillaQueries bleibt die "direct
 * vanilla path" für Headless-Tests, Pre-Boot-Szenarien und EngineLevers.abgeschaltet.
 * Wenn EngineMirror aktiv ist, ruft der Caller ({@link HumanoidAccessImpl})
 * EngineMirror-Layer auf — KEIN Loop, weil dieser Helper EngineMirror NICHT
 * konsumiert (defensive).</p>
 *
 * <p><b>Sprint v0.13.131+NoSilentFail (Compile-Fix):</b> LoggingAdapter-Aufrufe
 * nutzen Literal-Strings statt der Constants-Refactor — der ehemalige Refactor
 * platzierte {@code LoggingAdapter.Category.SEAM}, was nicht existiert
 * (SEAM liegt in der {@code Subsystem}-Klasse, nicht in {@code Category}).
 * Literal-Strings kompilieren direkt und sind binär-equivalent
 * (compile-time Konstanten-Inlining). Constants-Refactor ist separate Cleanup-Sprint-Aufgabe.</p>
 */
public final class VanillaQueries {

    private VanillaQueries() {}

    /**
     * @return Anzahl aller residents, oder 0 wenn die Vanilla-Engine nicht
     *         verfügbar ist. Konsistent mit {@link IHumanoidAccess#getResidentCount()}.
     *
     *         <p><b>Sprint v0.13.131+:</b> Single-Reflection-Pfad. size() deckt
     *         Collection/Map/Array/Primitive-Array. Bei Nicht-Match: 0 (war vorher
     *         silent "reflection-call failed").</p>
     */
    public static int residentCount() {
        try {
            Class<?> settClass = Class.forName("settlement.main.SETT");
            Object entities = settClass.getMethod("ENTITIES").invoke(null);
            if (entities == null) return 0;
            Object humansCollection = entities.getClass().getMethod("humans").invoke(entities);
            if (humansCollection == null) return 0;
            return robustSize(humansCollection);
        } catch (ReflectiveOperationException | LinkageError t) {
            // Engine noch nicht init oder Pfad nicht vorhanden — Sentinel.
            return 0;
        } catch (RuntimeException t) {
            // Reflection-Call hat geklappt, Runtime-Cast-/Null-/Class-Probleme unten —
            // das ist ein Mod-Bug, nicht Engine-Init-Defensive. LoggingAdapter statt
            // stderr damit der Eintrag im In-Game Debug-Tab statt nur im Launcher-Terminal
            // landet, und pandas-Filter (Subsystem=ECON, Category=SEAM) greifen.
            // Sprint v0.13.131+NoSilentFail-Hardening: String.valueOf statt t.getMessage()
            // weil die CSV-Row-Write-Pfad ein NPE hätte wenn der Throwable kein
            // Message-Feld setzt (z.B. `new NullPointerException()` ohne expliziten Text).
            LoggingAdapter.csvTrace("SEAM", "ECON", "WARN",
                    "vanilla_queries_resident_runtime",
                    t.getClass().getSimpleName(),
                    String.valueOf(t.getMessage()));
            return 0;
        }
    }

    /**
     * Iteriert über alle residents und ruft {@code action.accept(humanoid)}
     * pro Instanz auf. Wenn die Vanilla-Engine nicht verfügbar: no-op.
     *
     * <p><b>Sprint v0.13.131+:</b> asIterable() konvertiert Object[] transparent.
     * Vorher: ClassCastException bei Array-Typen → silent no-op.</p>
     */
    public static void forEachResident(Consumer<Humanoid> action) {
        if (action == null) return;
        try {
            Class<?> settClass = Class.forName("settlement.main.SETT");
            Object entities = settClass.getMethod("ENTITIES").invoke(null);
            if (entities == null) return;
            Object humansCollection = entities.getClass().getMethod("humans").invoke(entities);
            if (humansCollection == null) return;
            Iterable<?> iterable = asIterable(humansCollection);
            if (iterable == null) return;
            for (Object h : iterable) {
                if (h instanceof Humanoid humanoid) {
                    try {
                        action.accept(humanoid);
                    } catch (RuntimeException visitorException) {
                        // Eine fehlerhafte Visitor-Operation darf nicht die
                        // ganze Iteration abbrechen. Skip und continue.
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError t) {
            // Engine noch nicht ready oder Pfad nicht vorhanden — no-op (Boot-Defensive).
        } catch (RuntimeException t) {
            // Reflection hat geklappt, aber Runtime-Problem unten (Cast/Null/Class) —
            // das ist ein Mod-Bug, nicht Engine-Init. LoggingAdapter statt stderr
            // für konsistente In-Game-Visibility via Debug-Tab.
            // Sprint v0.13.131+NoSilentFail-Hardening: String.valueOf statt t.getMessage()
            // schützt den CSV-Write-Pfad vor NPE bei Throwables ohne Message-Text.
            LoggingAdapter.csvTrace("SEAM", "ECON", "WARN",
                    "vanilla_queries_foreach_runtime",
                    t.getClass().getSimpleName(),
                    String.valueOf(t.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Robust-Typ-Helpers — Sprint v0.13.131+
    // ══════════════════════════════════════════════════════════════

    /**
     * Robuste Größenbestimmung gegen alle gängigen Vanilla-Return-Typen
     * für {@code SETT.ENTITIES().humans()}. Reihenfolge der instanceof-
     * Checks entspricht der Auftrittswahrscheinlichkeit in Songs-of-Syx V71.44.
     */
    private static int robustSize(Object o) {
        if (o == null) return 0;
        if (o instanceof Collection<?> c) return c.size();
        if (o instanceof Map<?, ?> m) return m.size();
        if (o instanceof Object[] arr) return arr.length;
        if (o instanceof int[] arr) return arr.length;
        if (o instanceof long[] arr) return arr.length;
        if (o instanceof byte[] arr) return arr.length;
        if (o instanceof char[] arr) return arr.length;
        if (o instanceof double[] arr) return arr.length;
        if (o instanceof float[] arr) return arr.length;
        if (o instanceof boolean[] arr) return arr.length;
        // Last-ditch: reflection-call auf .size() — wirft NoSuchMethodException
        // bei Object[]-Pfaden ohne Iterable-Wrapper. Bewusst NICHT in
        // outer catch, sondern lokal behandelt.
        try {
            Object result = o.getClass().getMethod("size").invoke(o);
            if (result instanceof Number n) return n.intValue();
            return 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /**
     * Robuste Iterable-Konvertierung. Vor v0.13.131 schlug der Direct-
     * Cast {@code (Iterable<?>) humansCollection} bei Array-Rückgaben fehl.
     */
    private static Iterable<?> asIterable(Object o) {
        if (o == null) return null;
        if (o instanceof Iterable<?> i) return i;
        if (o instanceof Object[] arr) return Arrays.asList(arr);
        // Primitive Arrays: kein generischer Wrapper, foreach braucht Box.
        // Wir liefern einen synthetischen Iterable der die Box-Kopien macht.
        if (o instanceof int[] arr) {
            return () -> new java.util.Iterator<>() {
                int idx = 0;
                public boolean hasNext() { return idx < arr.length; }
                public Object next() { return arr[idx++]; }
            };
        }
        if (o instanceof long[] arr) {
            return () -> new java.util.Iterator<>() {
                int idx = 0;
                public boolean hasNext() { return idx < arr.length; }
                public Object next() { return arr[idx++]; }
            };
        }
        return null; // Primitive byte/char/double/float/boolean — nicht
                     // erwartet von Humans(), fail silently.
    }
}
