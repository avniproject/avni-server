package org.avni.server.web.request.rules.response;

import org.joda.time.DateTime;

public class ScheduleRuleResponseEntity extends BaseRuleResponseEntity {
    private DateTime scheduledDateTime;
    private Boolean shouldSend;

    public DateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    public void setScheduledDateTime(DateTime scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }

    public Boolean getShouldSend() {
        return shouldSend;
    }

    public void setShouldSend(Boolean shouldSend) {
        this.shouldSend = shouldSend;
    }
}
