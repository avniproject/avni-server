package org.avni.server.service;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class MetadataDiffServiceTest {

    @Test
    public void shouldTakeInTwoZipFilePaths() {
        Map result = new MetadataDiffService().compare("path1", "path2");
    }

}
