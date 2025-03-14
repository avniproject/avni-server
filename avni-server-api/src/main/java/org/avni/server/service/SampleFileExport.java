package org.avni.server.service;

import java.io.BufferedReader;
import java.io.IOException;

public interface SampleFileExport {
    String generateSampleFile(String[] uploadSpec);
}