package vannon.syx.economy.adapter;

import game.boosting.Boostable;

/**
 * Stats- und Boosting-Zugriff — globale Spielstatistiken (Time, Religion,
 * Weather, Environment) und BOOSTABLES-Konfiguration.
 *
 * <p>Teil des EngineMirror. Jeder Zugriff wird über {@code EngineLevers}
 * getogglet und via {@code LoggingAdapter} geloggt.</p>
 *
 * <p>Dieses Interface ist ein Stub für A-04b. Die Methoden für Time
 * ({@code workCycleSeconds}, {@code gameSecondsSinceStart}), Religion
 * ({@code religionCount}, {@code religionName}), und BOOSTABLES
 * ({@code getCivicGov}, {@code getBehaviourLoyalty}, etc.) werden in
 * einem zukünftigen Sprint implementiert.</p>
 *
 * <p>Version: V71.44.</p>
 */
public interface IStatsAccess {

    /**
     * @return true wenn die Stats-Engine verfügbar ist
     */
    boolean isAvailable();

    // ═══ Time ═══════════════════════════════════════════════

    /**
     * Work-Zyklus in Sekunden. {@code Math.max(1.0, TIME.workSeconds())}.
     *
     * @return Sekunden pro Work-Zyklus oder -1.0 bei Fehler
     */
    double getWorkCycleSeconds();

    /**
     * Spiel-Sekunden seit Start. {@code days.bitsSinceStart() * bitSeconds()
     * + secondOfBit()}.
     *
     * @return Gesamt-Sekunden oder -1.0 bei Fehler
     */
    double getGameSecondsSinceStart();

    // ═══ Religion Stats ═════════════════════════════════════

    /**
     * Anzahl der Religionen. {@code RELIGIONS.ALL().size()}.
     *
     * @return Religion-Anzahl oder 0
     */
    int getReligionCount();

    /**
     * Name einer Religion. {@code RELIGIONS.ALL().get(index).info.name}.
     *
     * @param index Religion-Index
     * @return Name oder "?"
     */
    CharSequence getReligionName(int index);

    // ═══ BOOSTABLES ═════════════════════════════════════════

    /**
     * Liefert das CIVICS.GOV Boostable (Admin-Bonus +20%).
     *
     * @return das Boostable oder null
     */
    Boostable getCivicGov();

    /**
     * Liefert ein Boostable aus der CIVICS-Kategorie per Feldname.
     *
     * @param fieldName Feldname (z.B. "SPOILAGE", "MAINTENANCE", "IMMIGRATION")
     * @return das Boostable oder null
     */
    Boostable getCivicBoostable(String fieldName);

    /**
     * Liefert ein Boostable aus der BEHAVIOUR-Kategorie per Feldname.
     *
     * @param fieldName Feldname (z.B. "LOYALTY", "HAPPI")
     * @return das Boostable oder null
     */
    Boostable getBehaviourBoostable(String fieldName);

    /**
     * Liefert die BOOSTABLES.CIVICS()-Instanz oder null.
     *
     * @return die CIVICS-Instanz
     */
    Object getCivicsInstance();

    /**
     * Liefert die BOOSTABLES.BEHAVIOUR()-Instanz oder null.
     *
     * @return die BEHAVIOUR-Instanz
     */
    Object getBehaviourInstance();

}
