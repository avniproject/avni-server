package org.avni.server.web.calendar;

import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.calendar.CalendarService;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.CalendarWorkingPatternValidator;
import org.avni.server.web.request.calendar.CalendarContract;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class CalendarController {

    // Matches DateTimeUtil.IST — calendars and markers are India-time-of-day concepts.
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CalendarService calendarService;
    private final CalendarRepository calendarRepository;
    private final CalendarDateMarkerRepository calendarDateMarkerRepository;
    private final LocationRepository locationRepository;
    private final AccessControlService accessControlService;

    public CalendarController(CalendarService calendarService,
                              CalendarRepository calendarRepository,
                              CalendarDateMarkerRepository calendarDateMarkerRepository,
                              LocationRepository locationRepository,
                              AccessControlService accessControlService) {
        this.calendarService = calendarService;
        this.calendarRepository = calendarRepository;
        this.calendarDateMarkerRepository = calendarDateMarkerRepository;
        this.locationRepository = locationRepository;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/calendar")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<CalendarContract> getAll() {
        List<Calendar> calendars = calendarRepository.findAllByIsVoidedFalse();
        Map<Long, Long> markerCountByCalendarId = currentYearMarkerCountByCalendarId();
        return calendars.stream().map(calendar -> {
            CalendarContract contract = CalendarContract.fromEntity(calendar);
            contract.setMarkerCountThisYear(markerCountByCalendarId.getOrDefault(calendar.getId(), 0L));
            return contract;
        }).collect(Collectors.toList());
    }

    private Map<Long, Long> currentYearMarkerCountByCalendarId() {
        LocalDate[] range = currentYearRange();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : calendarDateMarkerRepository.countByDateRangeGroupedByCalendar(range[0], range[1])) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private long currentYearMarkerCountFor(Calendar calendar) {
        LocalDate[] range = currentYearRange();
        return calendarDateMarkerRepository.countByCalendarAndMarkerDateBetweenAndIsVoidedFalse(calendar, range[0], range[1]);
    }

    private LocalDate[] currentYearRange() {
        int year = Year.now(IST).getValue();
        return new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
    }

    @GetMapping(value = "/web/calendar/{uuid}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<CalendarContract> getByUuid(@PathVariable String uuid) {
        Calendar calendar = calendarRepository.findByUuid(uuid);
        if (calendar == null) {
            return ResponseEntity.notFound().build();
        }
        CalendarContract contract = CalendarContract.fromEntity(calendar);
        contract.setMarkerCountThisYear(currentYearMarkerCountFor(calendar));
        return ResponseEntity.ok(contract);
    }

    @PostMapping(value = "/web/calendar")
    @ResponseBody
    @Transactional
    public ResponseEntity<CalendarContract> create(@RequestBody CalendarContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.ManageCalendars);
        contract.setupUuidIfNeeded();
        Calendar calendar = new Calendar();
        calendar.assignUUID(contract.getUuid());
        applyContract(contract, calendar);
        Calendar saved = calendarService.save(calendar);
        return ResponseEntity.ok(CalendarContract.fromEntity(saved));
    }

    @PutMapping(value = "/web/calendar/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<CalendarContract> update(@PathVariable String uuid, @RequestBody CalendarContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.ManageCalendars);
        Calendar calendar = calendarRepository.findByUuid(uuid);
        if (calendar == null) {
            return ResponseEntity.notFound().build();
        }
        applyContract(contract, calendar);
        Calendar saved = calendarService.save(calendar);
        return ResponseEntity.ok(CalendarContract.fromEntity(saved));
    }

    @DeleteMapping(value = "/web/calendar/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        accessControlService.checkPrivilege(PrivilegeType.ManageCalendars);
        Calendar calendar = calendarRepository.findByUuid(uuid);
        if (calendar == null) {
            return ResponseEntity.notFound().build();
        }
        calendarService.delete(calendar);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/web/calendar/{uuid}/set-default")
    @ResponseBody
    @Transactional
    public ResponseEntity<CalendarContract> setDefault(@PathVariable String uuid) {
        accessControlService.checkPrivilege(PrivilegeType.ManageCalendars);
        Calendar calendar = calendarRepository.findByUuid(uuid);
        if (calendar == null) {
            return ResponseEntity.notFound().build();
        }
        Calendar updated = calendarService.setDefault(calendar);
        return ResponseEntity.ok(CalendarContract.fromEntity(updated));
    }

    private void applyContract(CalendarContract contract, Calendar calendar) {
        calendar.setName(contract.getName());
        JsonObject workingPattern = contract.getWorkingPattern() == null ? defaultWorkingPattern() : new JsonObject(contract.getWorkingPattern());
        CalendarWorkingPatternValidator.validate(workingPattern);
        calendar.setWorkingPattern(workingPattern);
        if (contract.getAddressLevelUUID() != null) {
            AddressLevel addressLevel = locationRepository.findByUuid(contract.getAddressLevelUUID());
            if (addressLevel == null) {
                throw new BadRequestError("AddressLevel not found: %s", contract.getAddressLevelUUID());
            }
            calendar.setAddressLevel(addressLevel);
        } else {
            calendar.setAddressLevel(null);
        }
        calendar.setDefault(contract.isDefault());
        calendar.setVoided(contract.isVoided());
    }

    private JsonObject defaultWorkingPattern() {
        return new JsonObject()
                .with("mon", "all").with("tue", "all").with("wed", "all").with("thu", "all")
                .with("fri", "all").with("sat", "none").with("sun", "none");
    }
}
