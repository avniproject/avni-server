package org.avni.server.service.calendar;

import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ScopeAwareService;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService implements ScopeAwareService<Calendar> {
    private final CalendarRepository calendarRepository;

    public CalendarService(CalendarRepository calendarRepository) {
        this.calendarRepository = calendarRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String typeUUID) {
        User user = UserContextHolder.getUserContext().getUser();
        return isChangedByCatchment(user, lastModifiedDateTime, SyncEntityName.Calendar);
    }

    @Override
    public OperatingIndividualScopeAwareRepository<Calendar> repository() {
        return calendarRepository;
    }

    public Calendar save(Calendar calendar) {
        calendar.assignUUIDIfRequired();
        return calendarRepository.save(calendar);
    }

    @Transactional
    public Calendar setDefault(Calendar calendar) {
        calendarRepository.clearDefaultExcept(calendar.getUuid());
        calendar.setDefault(true);
        return calendarRepository.save(calendar);
    }
}
