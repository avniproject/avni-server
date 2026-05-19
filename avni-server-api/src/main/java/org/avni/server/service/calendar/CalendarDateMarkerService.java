package org.avni.server.service.calendar;

import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ScopeAwareService;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

@Service
public class CalendarDateMarkerService implements ScopeAwareService<CalendarDateMarker> {
    private final CalendarDateMarkerRepository markerRepository;

    public CalendarDateMarkerService(CalendarDateMarkerRepository markerRepository) {
        this.markerRepository = markerRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String typeUUID) {
        User user = UserContextHolder.getUserContext().getUser();
        return isChangedByCatchment(user, lastModifiedDateTime, SyncEntityName.CalendarDateMarker);
    }

    @Override
    public OperatingIndividualScopeAwareRepository<CalendarDateMarker> repository() {
        return markerRepository;
    }

    public CalendarDateMarker save(CalendarDateMarker marker) {
        marker.assignUUIDIfRequired();
        return markerRepository.save(marker);
    }
}
