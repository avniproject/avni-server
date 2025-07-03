package org.avni.server.domain.batch;

import java.util.Date;

public record BatchJobStatus(String status, Date createDateTime, Date startDateTime, Date endDateTime, String exitMessage, String exitCode) {
}
