package org.avni.server.domain.sync;

import org.avni.server.domain.Individual;
import org.joda.time.DateTime;

import java.util.List;

public class SyncDisabledEntityHelper {
    public static void handleSave(SubjectLinkedSyncEntity entity, Individual individual) {
        if (individual.isSyncDisabled()) {
            entity.setSyncDisabledDateTime(DateTime.now().toDate());
            entity.setSyncDisabled(true);
        }
    }

    public static void handleSave(SubjectLinkedSyncEntity entity, Individual individual1, Individual individual2) {
        if (individual1.isSyncDisabled() || individual2.isSyncDisabled()) {
            entity.setSyncDisabledDateTime(DateTime.now().toDate());
            entity.setSyncDisabled(true);
        }
    }

    public static void handleSave(SubjectLinkedSyncEntity entity, List<Individual> individuals) {
        if (individuals.stream().anyMatch(Individual::isSyncDisabled)) {
            entity.setSyncDisabledDateTime(DateTime.now().toDate());
            entity.setSyncDisabled(true);
        }
    }
}
