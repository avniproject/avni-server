package org.avni.server.importer.batch.csv.writer;

public interface CommonWriterError {
    String ERR_MSG_UNKNOWN_HEADERS = "Unknown headers - %s included in file. Please refer to sample file for valid list of headers.";
    String ERR_MSG_MISSING_MANDATORY_FIELDS = "Mandatory columns are missing from uploaded file - %s. Please refer to sample file for the list of mandatory headers.";
    String ERR_MSG_MISSING_MANDATORY_HEADER_FIELDS = "Mandatory columns are missing in header from uploaded file - %s. Please refer to sample file for the list of mandatory headers.";
    String ERR_MSG_DUPLICATE_HEADERS = "Headers %s are repeated. Please update the name or remove the duplicates.";
}
