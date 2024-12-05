package org.avni.server.service;

import org.joda.time.DateTime;

public interface DeviceAwareService {
    String WEB_DEVICE_ID = "web";

    boolean isSyncRequiredForDevice(DateTime lastModifiedDateTime, String deviceId);

}
