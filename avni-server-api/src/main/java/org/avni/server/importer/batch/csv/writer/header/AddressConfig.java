package org.avni.server.importer.batch.csv.writer.header;

import java.util.List;

public class AddressConfig {
    List<String> headers;
    int size;

    public AddressConfig(List<String> headers, int size) {
        this.headers = headers;
        this.size = size;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public int getSize() {
        return size;
    }
}
