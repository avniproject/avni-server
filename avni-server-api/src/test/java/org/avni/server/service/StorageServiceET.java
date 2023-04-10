package org.avni.server.service;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;

public class StorageServiceET {
    @Test
    public void downloadMediaToFileFromDrive() throws Exception {
        TestStorageService testStorageService = new TestStorageService();
        testStorageService.downloadMediaToFile("https://drive.google.com/uc?export=download&id=1fmbpdhy1LjMBQ7Lu-RddzMEUf8lJtrsL");
    }

    static class TestStorageService extends StorageService {
        private static final Logger logger = LoggerFactory.getLogger(TestStorageService.class);

        public TestStorageService() {
            super("foo-bucket", true, logger, true);
        }

        @Override
        public URL generateMediaDownloadUrl(String url) {
            return null;
        }

        @Override
        public InputStream getObjectContentFromUrl(String s3Url) {
            return null;
        }
    }
}
