package org.avni.server.domain;

public class ExportJobParametersBuilder {
    private final ExportJobParameters exportJobParameters = new ExportJobParameters();

    public ExportJobParametersBuilder withTimezone(String timezone) {
        exportJobParameters.setTimezone(timezone);
    	return this;
    }

    public ExportJobParameters build() {
        return exportJobParameters;
    }
}
