package vannon.syx.economy.core;

import java.io.IOException;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/**
 * Minimal save/load interface for economy subsystems chunks.
 *
 * Implementing classes must be able to serialize their entire mutable state to a
 * {@link FilePutter} and restore it from a {@link FileGetter}. The chunk wrapper in
 * {@link EconomySim} records the byte length of each subsystem's data, so a broken
 * or partially-read chunk can be safely skipped without affecting adjacent chunks.
 */
public interface Saveable {
    void save(FilePutter file);
    void load(FileGetter file) throws IOException;
}
