package vannon.syx.economy.core;

import java.io.IOException;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/**
 * Tag-Length-Value (TLV) helper for robust subsystem save/load.
 *
 * Each chunk has the on-disk layout:
 *   int  tag     (4 bytes)
 *   int  length  (4 bytes, number of payload BYTES)
 *   ...payload...
 *
 * Snake2D's FilePutter/FileGetter track positions in bytes (ByteBuffer.position()),
 * so chunk lengths are stored in bytes. Unknown chunks can be skipped safely on
 * load by jumping over the declared byte length.
 *
 * This class is intentionally minimal: it only records start/end positions and
 * writes a length prefix. It does not buffer data in memory.
 */
public final class ChunkedSave {

    private ChunkedSave() {
    }

    /**
     * Writes the chunk tag and a placeholder length, returning the byte position
     * where the length was written. Pass this position to {@link #endChunk}.
     */
    public static int startChunk(FilePutter file, int tag) {
        file.i(tag);
        int lengthPosition = file.getPosition();
        file.i(0);
        return lengthPosition;
    }

    /**
     * Calculates the chunk payload length (in bytes) and writes it back at the
     * position returned by {@link #startChunk}.
     */
    public static void endChunk(FilePutter file, int lengthPosition) {
        int end = file.getPosition();
        int length = end - (lengthPosition + 4); // payload bytes after the 4-byte length field
        file.setAtPosition(lengthPosition, length);
    }

    /** Header read from the stream for one chunk. */
    public static final class Header {
        public final int tag;
        public final int length;
        public final int dataPosition;

        public Header(int tag, int length, int dataPosition) {
            this.tag = tag;
            this.length = length;
            this.dataPosition = dataPosition;
        }
    }

    /**
     * Reads the next chunk header, or returns null if no more full chunks are
     * available. The caller can then either read the payload or skip it with
     * {@link #skipChunk}.
     */
    public static Header readHeader(FileGetter file) throws IOException {
        if (file.remainingInts() < 2) {
            return null;
        }
        int tag = file.i();
        int length = file.i();
        return new Header(tag, length, file.getPosition());
    }

    /**
     * Skips a chunk with the given header by advancing the file pointer past its
     * payload. Safe to call even if the chunk is empty. Length is in bytes.
     */
    public static void skipChunk(FileGetter file, Header header) {
        file.setPosition(header.dataPosition + header.length);
    }
}
