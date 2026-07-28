package vannon.syx.economy.core.save;

/**
 * Interface für Wirtschaftstakt-Orchestrierung.
 * Definiert den Vertrag für den Haupttakt-loop der Wirtschaftssimulation.
 */
public interface IEconomyTick {

    /**
     * Führt einen Wirtschafts-Takt mit der gegebenen Zeitverschiebung aus.
     * 
     * @param ds Zeitdelta in Sekunden seit letztem Tick
     */
    void tick(double ds);

    /**
     * Gibt die auszulösenden Phasen-Triggers für den aktuellen Takt zurück.
     * 
     * @return Array der aktiven Phasen-Triggers
     */
    int[] phaseTriggers();

    /**
     * Gibt den Re-Entry-Wächter zurück.
     * 
     * @return Der aktuelle Re-Entry-Wächter
     */
    Object reentryGuard();

    /**
     * Behandelt die Tag/Nacht-Transition.
     * Wird einmal pro ingame-Tag aufgerufen.
     */
    void dayBoundary();
}
