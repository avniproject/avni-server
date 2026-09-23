package org.avni.server.util;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileUtilTest {
    private static final byte[] BOM_BYTES = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    public void bomIsTheThreeBytesExcelLooksFor() {
        byte[] encoded = FileUtil.UTF8_BOM.getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(BOM_BYTES, encoded);
    }

    @Test
    public void withUtf8BomLeadsTheContentAndLeavesItOtherwiseIntact() {
        String header = "ind.id,\"ಶಾಲೆಯ ಹೆಸರು\"";
        String withBom = FileUtil.withUtf8Bom(header);

        assertEquals(FileUtil.UTF8_BOM + header, withBom);
        assertEquals(header, FileUtil.stripUtf8Bom(withBom));
    }

    @Test
    public void withUtf8BomDoesNotStackMarkers() {
        String once = FileUtil.withUtf8Bom("a,b");
        assertEquals(once, FileUtil.withUtf8Bom(once));
    }

    @Test
    public void withUtf8BomHandlesNothingToWrite() {
        assertEquals(FileUtil.UTF8_BOM, FileUtil.withUtf8Bom(null));
        assertEquals(FileUtil.UTF8_BOM, FileUtil.withUtf8Bom(""));
    }

    @Test
    public void stripUtf8BomToleratesNull() {
        assertEquals(null, FileUtil.stripUtf8Bom(null));
    }
}
