package org.avni.server.service.calendar;

import org.avni.server.common.EntityHelper;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ScopeAwareService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.calendar.CalendarDateMarkerContract;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarDateMarkerService implements ScopeAwareService<CalendarDateMarker> {
    private final CalendarDateMarkerRepository markerRepository;
    private final CalendarRepository calendarRepository;

    public CalendarDateMarkerService(CalendarDateMarkerRepository markerRepository,
                                     CalendarRepository calendarRepository) {
        this.markerRepository = markerRepository;
        this.calendarRepository = calendarRepository;
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

    @Transactional
    public void saveFromBundle(CalendarDateMarkerContract[] contracts) {
        for (CalendarDateMarkerContract contract : contracts) {
            CalendarDateMarker marker = EntityHelper.newOrExistingEntity(markerRepository, contract.getUuid(), new CalendarDateMarker());
            Calendar calendar = calendarRepository.findByUuid(contract.getCalendarUUID());
            if (calendar == null) {
                throw new BadRequestError("CalendarDateMarker bundle row references unknown Calendar uuid: %s", contract.getCalendarUUID());
            }
            marker.setCalendar(calendar);
            marker.setMarkerDate(contract.getMarkerDate());
            marker.setName(contract.getName());
            marker.setWorking(contract.isWorking());
            marker.setVoided(contract.isVoided());
            save(marker);
        }
    }
}
