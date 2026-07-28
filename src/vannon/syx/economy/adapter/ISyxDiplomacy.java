package vannon.syx.economy.adapter;

import game.faction.diplomacy.DipWarPlayer;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;

/**
 * Kapselt die Reflection-Zugriffe auf die vier privaten Felder der
 * {@link DipWarPlayer}-Klasse in Songs of Syx V71.44:
 *
 * <ul>
 *   <li>{@code upI}          — int      Update-Index (verhindert Doppel-Berechnung)</li>
 *   <li>{@code pPow}         — double   Spieler-Macht  (geschrieben)</li>
 *   <li>{@code coalitionPow} — double   Koalitions-Macht  (geschrieben)</li>
 *   <li>{@code bWilling}     — {@link Bitmap1D}  Bitmap kriegswilliger Fraktionen  (mutable referenz)</li>
 * </ul>
 *
 * <p>{@code getWillingList()} nutzt den <strong>öffentlichen</strong> Getter
 * {@code DipWarPlayer.willing()}, der exakt dieselbe Referenz zurückgibt wie
 * das private Feld — kein Reflection nötig.</p>
 *
 * <p>Bei einem Spiel-Update muss nur die Adapter-Implementierung
 * geprüft werden — nicht mehr der gesamte {@code DebtDiplomacyBuffer}.</p>
 */
public interface ISyxDiplomacy {

    /**
     * @return true wenn die vier Reflection-Felder alle erfolgreich aufgelöst
     *         wurden; false wenn der Fallback genutzt werden muss (Spiel-Update,
     *         Engine-API-Änderung).
     */
    boolean isAvailable();

    /**
     * Liefert die mutable {@code war.bWilling}-Referenz. Der Aufrufer darf sie
     * in-place manipulieren ({@code clear()}, {@code set(i, true)}).
     *
     * @param war DipWarPlayer-Instanz
     * @return Bitmap der kriegswilligen Fraktionen
     */
    Bitmap1D getWillingBits(DipWarPlayer war);

    /**
     * Liefert die mutable {@code war.willing()}-Referenz (public Getter,
     * kein Reflection). Der Aufrufer darf sie in-place manipulieren
     * ({@code clearSloppy()}, {@code add(faction)}).
     *
     * @param war DipWarPlayer-Instanz
     * @return Liste der kriegswilligen Fraktionen
     */
    ArrayList<?> getWillingList(DipWarPlayer war);

    /**
     * Schreibt die drei numerischen Felder atomar. Wenn ein einzelner
     * Write scheitert, wird der Adapter intern deaktiviert (isAvailable
     * returnt dann false); Folge-Aufrufe sind No-ops.
     *
     * @param war            DipWarPlayer-Instanz
     * @param updateIndex    Wert für {@code war.upI}
     * @param playerPower    Wert für {@code war.pPow}
     * @param coalitionPower Wert für {@code war.coalitionPow}
     */
    void setNumericState(DipWarPlayer war,
                         int updateIndex,
                         double playerPower,
                         double coalitionPower);
}
