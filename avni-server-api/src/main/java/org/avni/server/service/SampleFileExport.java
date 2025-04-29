package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.config.InvalidConfigurationException;

public interface SampleFileExport {
    String generateSampleFile(String[] uploadSpec, Object mode) throws InvalidConfigurationException;
    FormMapping getFormMapping(String[] uploadSpec);
}
