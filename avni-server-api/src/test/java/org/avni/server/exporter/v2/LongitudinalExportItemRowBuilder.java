package org.avni.server.exporter.v2;

import org.avni.server.domain.Individual;

public class LongitudinalExportItemRowBuilder {
    private final LongitudinalExportItemRow longitudinalExportItemRow = new LongitudinalExportItemRow();

    public LongitudinalExportItemRowBuilder withSubject(Individual subject) {
        longitudinalExportItemRow.setIndividual(subject);
    	return this;
    }

    public LongitudinalExportItemRow build() {
        return longitudinalExportItemRow;
    }
}
