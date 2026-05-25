package org.avni.server.web.calendar;

import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.calendar.CalendarDateMarkerService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.calendar.CalendarDateMarkerContract;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class CalendarDateMarkerController {

    private final CalendarDateMarkerService markerService;
    private final CalendarDateMarkerRepository markerRepository;
    private final CalendarRepository calendarRepository;
    private final AccessControlService accessControlService;

    public CalendarDateMarkerController(CalendarDateMarkerService markerService,
                                        CalendarDateMarkerRepository markerRepository,
                                        CalendarRepository calendarRepository,
                                        AccessControlService accessControlService) {
        this.markerService = markerService;
        this.markerRepository = markerRepository;
        this.calendarRepository = calendarRepository;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/calendarDateMarker")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<CalendarDateMarkerContract> getAll(@RequestParam(value = "calendarUUID", required = false) String calendarUUID,
                                                   @RequestParam(value = "year", required = false) Integer year) {
        Calendar calendar = calendarUUID == null ? null : calendarRepository.findByUuid(calendarUUID);
        if (calendarUUID != null && calendar == null) {
            return List.of();
        }
        LocalDate start = year == null ? null : LocalDate.of(year, 1, 1);
        LocalDate end = year == null ? null : LocalDate.of(year, 12, 31);

        List<CalendarDateMarker> markers;
        if (calendar != null && year != null) {
            markers = markerRepository.findByCalendarAndMarkerDateBetweenAndIsVoidedFalse(calendar, start, end);
        } else if (calendar != null) {
            markers = markerRepository.findByCalendarAndIsVoidedFalse(calendar);
        } else if (year != null) {
            markers = markerRepository.findByMarkerDateBetweenAndIsVoidedFalse(start, end);
        } else {
            markers = markerRepository.findAllByIsVoidedFalse();
        }
        return markers.stream().map(CalendarDateMarkerContract::fromEntity).collect(Collectors.toList());
    }

    @GetMapping(value = "/web/calendarDateMarker/{uuid}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<CalendarDateMarkerContract> getByUuid(@PathVariable String uuid) {
        CalendarDateMarker marker = markerRepository.findByUuid(uuid);
        if (marker == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(CalendarDateMarkerContract.fromEntity(marker));
    }

    @PostMapping(value = "/web/calendarDateMarker")
    @ResponseBody
    @Transactional
    public ResponseEntity<CalendarDateMarkerContract> create(@RequestBody CalendarDateMarkerContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.ManageCalendars);
        contract.setupUuidIfNeeded();
        CalendarDateMarker marker = new CalendarDateMarker();
        marker.assignUUID(contract.getUuid());
        applyContract(contract, marker);
        CalendarDateMarker saved = markerService.save(marker);
        return ResponseEntity.ok(CalendarDateMarkerContract.fromEntity(saved));
    }

    @PutMapping(value = "/web/calendarDateMarker/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<CalendarDateMarkerContract> update(@PathVariable String uuid, @RequestBody CalendarDateMarkerContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.ManageCalendars);
        CalendarDateMarker marker = markerRepository.findByUuid(uuid);
        if (marker == null) {
            return ResponseEntity.notFound().build();
        }
        applyContract(contract, marker);
        CalendarDateMarker saved = markerService.save(marker);
        return ResponseEntity.ok(CalendarDateMarkerContract.fromEntity(saved));
    }

    @DeleteMapping(value = "/web/calendarDateMarker/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        accessControlService.checkPrivilege(PrivilegeType.ManageCalendars);
        CalendarDateMarker marker = markerRepository.findByUuid(uuid);
        if (marker == null) {
            return ResponseEntity.notFound().build();
        }
        marker.setVoided(true);
        markerRepository.save(marker);
        return ResponseEntity.ok().build();
    }

    private void applyContract(CalendarDateMarkerContract contract, CalendarDateMarker marker) {
        Calendar calendar = calendarRepository.findByUuid(contract.getCalendarUUID());
        if (calendar == null) {
            throw new BadRequestError("Calendar not found: %s", contract.getCalendarUUID());
        }
        marker.setCalendar(calendar);
        marker.setMarkerDate(contract.getMarkerDate());
        marker.setName(contract.getName());
        marker.setWorking(contract.isWorking());
        marker.setVoided(contract.isVoided());
    }
}
