package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;

public interface HeaderCreator {
    //We will eventually get rid of this method(without any parameters) after we have refactored code for all other sample files/header files like encounterheaders , locationheaders etc
    String[] getAllHeaders();
    String[] getAllHeaders(FormMapping formMapping);
    String[] getConceptHeaders(FormMapping formMapping, String[] fileHeaders);
    String[] getAllDescriptions(FormMapping formMapping);
}
