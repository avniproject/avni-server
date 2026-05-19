package org.avni.server.web.calendar;

import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.calendar.CalendarService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.calendar.CalendarContract;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class CalendarController {

    private final CalendarService calendarService;
    private final CalendarRepository calendarRepository;
    private final LocationRepository locationRepository;
    private final AccessControlService accessControlService;

    public CalendarController(CalendarService calendarService,
                              CalendarRepository calendarRepository,
                              LocationRepository locationRepository,
                              AccessControlService accessControlService) {
        this.calendarService = calendarService;
        this.calendarRepository = calendarRepository;
        this.locationRepository = locationRepository;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/calendar")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<CalendarContract> getAll() {
        return calendarRepository.findAllByIsVoidedFalse()
                .stream().map(CalendarContract::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/calendar/{uuid}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<CalendarContract> getByUuid(@PathVariable String uuid) {
        Calendar calendar = calendarRepository.findByUuid(uuid);
        if (calendar == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(CalendarContract.fromEntity(calendar));
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
        calendar.setVoided(true);
        calendarRepository.save(calendar);
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
        calendar.setWorkingPattern(contract.getWorkingPattern() == null ? defaultWorkingPattern() : new JsonObject(contract.getWorkingPattern()));
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
