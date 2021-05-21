package org.openchs.dao;

import org.joda.time.DateTime;
import org.openchs.domain.AddressLevel;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class SyncParameters {
    private final long facilityId;
    private final long catchmentId;
    private final DateTime lastModifiedDateTime;
    private final DateTime now;
    private final Long filter;
    private final Pageable pageable;
    private List<AddressLevel> addressLevels;

    public SyncParameters(long facilityId, long catchmentId, DateTime lastModifiedDateTime, DateTime now, Long filter, Pageable pageable, List<AddressLevel> addressLevels) {
        this.facilityId = facilityId;
        this.catchmentId = catchmentId;
        this.lastModifiedDateTime = lastModifiedDateTime;
        this.now = now;
        this.filter = filter;
        this.pageable = pageable;
        this.addressLevels = addressLevels;
    }

    public long getCatchmentId() {
        return catchmentId;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public DateTime getNow() {
        return now;
    }

    public Long getFilter() {
        return filter;
    }

    public Pageable getPageable() {
        return pageable;
    }

    public long getFacilityId() {
        return facilityId;
    }

    public List<AddressLevel> getAddressLevels() {
        return addressLevels;
    }
}
