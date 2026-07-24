package vannon.syx.economy.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ChunkedSave} TLV chunk writer/reader.
 *
 * <p>Strategy: snake2d's {@link FileGetter} / {@link FilePutter} are {@code final}
 * classes that are NOT {@code AutoCloseable}. {@link FilePutter#save()} commits the
 * underlying buffer to disk; {@link FileGetter#close()} releases the buffer.
 * {@code @AfterEach} removes the temp file.</p>
 */
class ChunkedSaveTest {

    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("chunked-save-test-", ".syx");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void roundtrip_singleChunk_readHeaderReturnsExpectedTagAndLength() throws IOException {
        FilePutter putter = new FilePutter(tempFile, 256);
        int lengthPos = ChunkedSave.startChunk(putter, 0xABCD);
        putter.i(42);
        putter.i(43);
        ChunkedSave.endChunk(putter, lengthPos);
        putter.save();

        FileGetter getter = new FileGetter(tempFile);
        try {
            ChunkedSave.Header h = ChunkedSave.readHeader(getter);
            assertNotNull(h, "first chunk header must be readable");
            assertEquals(0xABCD, h.tag, "tag must round-trip");
            assertEquals(8, h.length, "payload length must include both ints (8 bytes)");
            assertEquals(42, getter.i(), "first payload int must round-trip");
            assertEquals(43, getter.i(), "second payload int must round-trip");
        } finally {
            getter.close();
        }
    }

    @Test
    void roundtrip_multipleChunks_skipPreservesBoundaries() throws IOException {
        FilePutter putter = new FilePutter(tempFile, 256);
        int lenA = ChunkedSave.startChunk(putter, 0x01);
        putter.i(100);
        putter.i(101);
        ChunkedSave.endChunk(putter, lenA);

        int lenB = ChunkedSave.startChunk(putter, 0x02);
        putter.i(200);
        putter.i(201);
        putter.i(202);
        ChunkedSave.endChunk(putter, lenB);

        int lenC = ChunkedSave.startChunk(putter, 0x03);
        putter.i(300);
        ChunkedSave.endChunk(putter, lenC);

        putter.save();

        FileGetter getter = new FileGetter(tempFile);
        try {
            ChunkedSave.Header a = ChunkedSave.readHeader(getter);
            assertNotNull(a);
            assertEquals(0x01, a.tag);
            assertEquals(8, a.length);
            assertEquals(100, getter.i());
            assertEquals(101, getter.i());

            ChunkedSave.Header b = ChunkedSave.readHeader(getter);
            assertNotNull(b);
            assertEquals(0x02, b.tag);
            assertEquals(12, b.length);

            ChunkedSave.skipChunk(getter, b);

            ChunkedSave.Header c = ChunkedSave.readHeader(getter);
            assertNotNull(c, "third chunk must be readable after skipChunk of B");
            assertEquals(0x03, c.tag);
            assertEquals(4, c.length);
            assertEquals(300, getter.i());

            assertNull(ChunkedSave.readHeader(getter),
                "readHeader must return null once all chunks are exhausted");
        } finally {
            getter.close();
        }
    }

    @Test
    void readHeader_emptyStream_returnsNull() throws IOException {
        FilePutter putter = new FilePutter(tempFile, 64);
        putter.save();

        FileGetter getter = new FileGetter(tempFile);
        try {
            assertNull(ChunkedSave.readHeader(getter),
                "an empty stream has no readable chunk headers");
        } finally {
            getter.close();
        }
    }

    @Test
    void readHeader_partialHeader_returnsNull() throws IOException {
        FilePutter putter = new FilePutter(tempFile, 64);
        putter.i(0xCAFE);
        putter.save();

        FileGetter getter = new FileGetter(tempFile);
        try {
            assertNull(ChunkedSave.readHeader(getter),
                "when fewer than 2 ints remain, readHeader must return null");
        } finally {
            getter.close();
        }
    }

    @Test
    void emptyChunk_zeroLengthPayload_isReadCorrectly() throws IOException {
        FilePutter putter = new FilePutter(tempFile, 128);
        int lenPos = ChunkedSave.startChunk(putter, 0x77);
        ChunkedSave.endChunk(putter, lenPos);
        putter.save();

        FileGetter getter = new FileGetter(tempFile);
        try {
            ChunkedSave.Header h = ChunkedSave.readHeader(getter);
            assertNotNull(h);
            assertEquals(0x77, h.tag);
            assertEquals(0, h.length, "zero-payload chunk must declare length=0");
            assertNull(ChunkedSave.readHeader(getter),
                "after a zero-length chunk, no further ints remain");
        } finally {
            getter.close();
        }
    }

    @Test
    void skipChunk_zeroLength_doesNotAdvance() throws IOException {
        FilePutter putter = new FilePutter(tempFile, 128);
        int lenPos = ChunkedSave.startChunk(putter, 0x88);
        ChunkedSave.endChunk(putter, lenPos);
        putter.save();

        FileGetter getter = new FileGetter(tempFile);
        try {
            ChunkedSave.Header h = ChunkedSave.readHeader(getter);
            assertNotNull(h);
            int beforeSkip = getter.getPosition();
            ChunkedSave.skipChunk(getter, h);
            assertEquals(beforeSkip, getter.getPosition(),
                "skipping a zero-length chunk must not advance the cursor");
        } finally {
            getter.close();
        }
    }

    @Test
    void endChunk_writesActualByteLengthNotDeclaredOne() throws IOException {
        FilePutter putter = new FilePutter(tempFile, 256);
        putter.i(0xBEEF);
        int lengthPos = putter.getPosition();
        putter.i(0);
        putter.i(10);
        putter.i(20);
        putter.i(30);
        ChunkedSave.endChunk(putter, lengthPos);
        putter.save();

        FileGetter getter = new FileGetter(tempFile);
        try {
            ChunkedSave.Header h = ChunkedSave.readHeader(getter);
            assertEquals(0xBEEF, h.tag);
            assertEquals(12, h.length, "endChunk must compute exact byte length");
            assertEquals(10, getter.i());
            assertEquals(20, getter.i());
            assertEquals(30, getter.i());
        } finally {
            getter.close();
        }
    }
}
