package vannon.syx.economy.core.save;

import java.io.IOException;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/**
 * Interface für Save/Load-Operationen der EconomySim.
 * Definiert den Vertrag für das Speichern und Laden von Wirtschaftszuständen.
 */
public interface IEconomySaveLoad {

    /**
     * Speichert den aktuellen Wirtschaftszustand.
     * 
     * @param filePutter Der FilePutter zum Schreiben der Daten
     */
    void save(FilePutter filePutter);

    /**
     * Lädt den Wirtschaftszustand aus einer Datei.
     * 
     * @param fileGetter Der FileGetter zum Lesen der Daten
     * @throws IOException wenn das Lesen fehlschlägt
     */
    void load(FileGetter fileGetter) throws IOException;

    /**
     * Setzt den Zustand beim Laden zurück.
     * Wird aufgerufen, nachdem alle Daten geladen wurden.
     */
    void resetOnLoad();

    /**
     * Gibt die Chunk-Tags zurück, die für das chunkbasierte Speichern verwendet werden.
     * 
     * @return Map der Chunk-Tags und ihre Beschreibungen
     */
    java.util.Map<String, String> chunkTags();

    /**
     * Gibt das Formatversionsnummer zurück.
     * Wird verwendet, um verschiedene Save-Formate zu unterscheiden.
     * 
     * @return Die Formatversionsnummer
     */
    int formatVersion();
}
