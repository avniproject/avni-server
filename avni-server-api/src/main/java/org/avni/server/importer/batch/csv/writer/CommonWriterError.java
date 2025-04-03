package org.avni.server.importer.batch.csv.writer;

public interface CommonWriterError {
    String ERR_MSG_UNKNOWN_HEADERS = "Unknown headers - %s included in file. Please refer to sample file for valid list of headers.";
    String ERR_MSG_MISSING_MANDATORY_FIELDS = "Mandatory columns are missing from uploaded file - %s. Please refer to sample file for the list of mandatory headers.";
}
