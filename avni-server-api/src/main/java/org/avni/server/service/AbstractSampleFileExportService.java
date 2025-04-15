package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.importer.batch.csv.writer.header.HeaderCreator;
import org.avni.server.importer.batch.csv.writer.header.Mode;

import static org.avni.server.service.ImportLocationsConstants.STRING_CONSTANT_SEPARATOR;

public abstract class AbstractSampleFileExportService implements SampleFileExport {

    @Override
    public String generateSampleFile(String[] uploadSpec, Mode mode) {
        FormMapping formMapping = getFormMapping(uploadSpec);

        StringBuilder sampleFileBuilder = new StringBuilder();
        HeaderCreator headers = getHeaders();
        sampleFileBuilder.append(String.join(STRING_CONSTANT_SEPARATOR, headers.getAllHeaders(formMapping, mode))).append("\n");
        sampleFileBuilder.append(String.join(STRING_CONSTANT_SEPARATOR, headers.getAllDescriptions(formMapping, mode))).append("\n");
        return sampleFileBuilder.toString();
    }

    protected abstract HeaderCreator getHeaders();

    public abstract FormMapping getFormMapping(String[] uploadSpec);
}
