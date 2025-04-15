package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;

import java.util.List;

public interface EncounterHeaderStrategy {
    List<HeaderField> generateHeaders(FormMapping formMapping);
}