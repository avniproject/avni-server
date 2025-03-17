package org.avni.server.service;

import org.avni.server.application.FormMapping;

public interface SampleFileExport {
    String generateSampleFile(String[] uploadSpec);
    FormMapping getFormMapping(String[] uploadSpec);
}