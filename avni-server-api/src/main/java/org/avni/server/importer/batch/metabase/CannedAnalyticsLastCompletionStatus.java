package org.avni.server.importer.batch.metabase;

// Based on the last completion of the batch job. This doesn't capture the current status of any running batch job.
public enum CannedAnalyticsLastCompletionStatus {
    NotEnabled, NotSetup, Setup, EtlNotRun
}
