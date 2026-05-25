package org.avni.server.service.calendar;

import org.avni.server.common.EntityHelper;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.User;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ScopeAwareService;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.CalendarWorkingPatternValidator;
import org.avni.server.web.request.calendar.CalendarContract;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class CalendarService implements ScopeAwareService<Calendar> {
    private final CalendarRepository calendarRepository;
    private final CalendarDateMarkerRepository calendarDateMarkerRepository;
    private final LocationRepository locationRepository;

    public CalendarService(CalendarRepository calendarRepository,
                           CalendarDateMarkerRepository calendarDateMarkerRepository,
                           LocationRepository locationRepository) {
        this.calendarRepository = calendarRepository;
        this.calendarDateMarkerRepository = calendarDateMarkerRepository;
        this.locationRepository = locationRepository;
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

    @Transactional
    public Calendar save(Calendar calendar) {
        calendar.assignUUIDIfRequired();
        if (!calendar.isVoided()) {
            List<Calendar> otherNonVoided = otherNonVoidedCalendars(calendar);
            enforceModeInvariants(calendar, otherNonVoided);
            if (!calendar.isDefault() && otherNonVoided.isEmpty()) {
                calendar.setDefault(true);
            }
        }
        return calendarRepository.save(calendar);
    }

    @Transactional
    public Calendar setDefault(Calendar calendar) {
        calendarRepository.clearDefaultExcept(calendar.getUuid());
        calendar.setDefault(true);
        return calendarRepository.save(calendar);
    }

    @Transactional
    public Calendar delete(Calendar calendar) {
        if (calendar.isVoided()) {
            return calendar;
        }
        List<Calendar> otherNonVoided = otherNonVoidedCalendars(calendar);
        boolean wasDefault = calendar.isDefault();
        calendar.setVoided(true);
        calendar.setDefault(false);
        Calendar saved = calendarRepository.save(calendar);
        voidMarkersFor(calendar);
        if (wasDefault) {
            promoteNextDefault(otherNonVoided);
        }
        return saved;
    }

    private void enforceModeInvariants(Calendar candidate, List<Calendar> otherNonVoided) {
        boolean candidateIsGlobal = candidate.getAddressLevel() == null;
        for (Calendar existing : otherNonVoided) {
            boolean existingIsGlobal = existing.getAddressLevel() == null;
            if (candidateIsGlobal && !existingIsGlobal) {
                throw new BadRequestError("Cannot create a global calendar while per-location calendars exist; void them first");
            }
            if (!candidateIsGlobal && existingIsGlobal) {
                throw new BadRequestError("Cannot create a per-location calendar while a global calendar exists; void it first");
            }
            if (!candidateIsGlobal && !existingIsGlobal
                    && existing.getAddressLevel().getId().equals(candidate.getAddressLevel().getId())) {
                throw new BadRequestError("A calendar already exists for this address level");
            }
        }
    }

    private boolean sameRow(Calendar a, Calendar b) {
        if (b.getId() != null) {
            return b.getId().equals(a.getId());
        }
        return b.getUuid() != null && b.getUuid().equals(a.getUuid());
    }

    private List<Calendar> otherNonVoidedCalendars(Calendar candidate) {
        return calendarRepository.findAllByIsVoidedFalse().stream()
                .filter(c -> !sameRow(c, candidate))
                .toList();
    }

    private void voidMarkersFor(Calendar calendar) {
        List<CalendarDateMarker> markers = calendarDateMarkerRepository.findByCalendarAndIsVoidedFalse(calendar);
        for (CalendarDateMarker marker : markers) {
            marker.setVoided(true);
        }
        calendarDateMarkerRepository.saveAll(markers);
    }

    private void promoteNextDefault(List<Calendar> candidates) {
        candidates.stream()
                .min(Comparator.comparing(Calendar::getId))
                .ifPresent(c -> {
                    c.setDefault(true);
                    calendarRepository.save(c);
                });
    }

    // A bad row (mode-invariant violation, unknown FK) throws and aborts this whole file; the Spring Batch
    // importer marks calendars.json as skipped in errors.csv and continues to the next file. This matches the
    // existing per-file bundle-import behaviour rather than the per-row failedRows shape in the story spec.
    @Transactional
    public void saveFromBundle(CalendarContract[] contracts) {
        for (CalendarContract contract : contracts) {
            Calendar calendar = EntityHelper.newOrExistingEntity(calendarRepository, contract.getUuid(), new Calendar());
            calendar.setName(contract.getName());
            JsonObject workingPattern = contract.getWorkingPattern() == null ? null : new JsonObject(contract.getWorkingPattern());
            if (workingPattern != null) {
                CalendarWorkingPatternValidator.validate(workingPattern);
            }
            calendar.setWorkingPattern(workingPattern);
            calendar.setDefault(contract.isDefault());
            if (contract.getAddressLevelUUID() != null) {
                AddressLevel addressLevel = locationRepository.findByUuid(contract.getAddressLevelUUID());
                if (addressLevel == null) {
                    throw new BadRequestError("Calendar bundle row references unknown AddressLevel uuid: %s", contract.getAddressLevelUUID());
                }
                calendar.setAddressLevel(addressLevel);
            } else {
                calendar.setAddressLevel(null);
            }
            calendar.setVoided(contract.isVoided());
            save(calendar);
        }
    }
}
