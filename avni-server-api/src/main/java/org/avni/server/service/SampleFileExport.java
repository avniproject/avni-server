package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.importer.batch.csv.writer.header.Mode;

public interface SampleFileExport {
    String generateSampleFile(String[] uploadSpec, Mode mode);
    FormMapping getFormMapping(String[] uploadSpec);
}