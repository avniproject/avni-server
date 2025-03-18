package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.domain.SubjectType;

import java.util.List;

public interface Headers {
    //We will eventually get rid of this method(without any parameters) after we have refactored code for all other sample files/header files like encounterheaders , locationheaders etc
    String[] getAllHeaders();
    String[] getAllHeaders(FormMapping formMapping);
    String[] getAllDescriptions(FormMapping formMapping);
}
