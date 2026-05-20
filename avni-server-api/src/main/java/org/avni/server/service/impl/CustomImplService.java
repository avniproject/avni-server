package org.avni.server.service.impl;

import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.impl.CustomImplRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.response.ResponsePage;
import org.avni.server.web.response.impl.CatchmentLocationNode;
import org.avni.server.web.response.impl.CatchmentLocationsResponse;
import org.avni.server.web.response.impl.EncounterWithLocationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomImplService {
    private static final int MAX_PAGE_SIZE = 200;

    private final EncounterRepository encounterRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final CustomImplRepository customImplRepository;

    @Autowired
    public CustomImplService(EncounterRepository encounterRepository,
                             EncounterTypeRepository encounterTypeRepository,
                             CustomImplRepository customImplRepository) {
        this.encounterRepository = encounterRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.customImplRepository = customImplRepository;
    }

    public CatchmentLocationsResponse getCatchmentLocationsForCurrentUser() {
        User user = UserContextHolder.getUserContext().getUser();
        if (user == null) return new CatchmentLocationsResponse(List.of(), List.of());

        Catchment catchment = user.getCatchment();
        if (catchment == null || catchment.getAddressLevels().isEmpty()) {
            return new CatchmentLocationsResponse(List.of(), List.of());
        }

        Set<AddressLevel> visited = new LinkedHashSet<>();
        Set<String> rootUuids = new LinkedHashSet<>();
        for (AddressLevel leaf : catchment.getAddressLevels()) {
            AddressLevel cursor = leaf;
            while (cursor != null) {
                visited.add(cursor);
                if (cursor.getParent() == null) rootUuids.add(cursor.getUuid());
                cursor = cursor.getParent();
            }
        }

        List<CatchmentLocationNode> nodes = visited.stream()
                .filter(al -> !al.isVoided())
                .map(al -> new CatchmentLocationNode(
                        al.getUuid(),
                        al.getTitle(),
                        al.getType().getName(),
                        al.getParent() == null ? null : al.getParent().getUuid()))
                .collect(Collectors.toList());
        return new CatchmentLocationsResponse(nodes, List.copyOf(rootUuids));
    }

    public ResponsePage findEncountersWithLocation(
            String encounterTypeName,
            EncounterStatus status,
            String locationUuid,
            Pageable pageable) {

        if (encounterTypeName == null || encounterTypeName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "encounterType is required");
        }
        EncounterType encounterType = encounterTypeRepository.findByName(encounterTypeName);
        if (encounterType == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Encounter type '" + encounterTypeName + "' not found");
        }

        List<Long> descendantIds = null;
        if (locationUuid != null && !locationUuid.isBlank()) {
            descendantIds = customImplRepository.findSubtreeAddressLevelIds(locationUuid);
            if (descendantIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Location '" + locationUuid + "' not found");
            }
        }

        Pageable capped = capSize(pageable);

        Specification<Encounter> spec = Specification
                .where(notVoided())
                .and(withEncounterTypeId(encounterType.getId()))
                .and(withStatus(status))
                .and(withSubjectInLocations(descendantIds));

        Page<Encounter> page = encounterRepository.findAll(spec, capped);
        List<EncounterWithLocationResponse> content = page.getContent().stream()
                .map(EncounterWithLocationResponse::from)
                .collect(Collectors.toList());
        return new ResponsePage(content,
                (int) page.getTotalElements(),
                page.getTotalPages(),
                page.getSize());
    }

    private static Specification<Encounter> notVoided() {
        return (root, q, cb) -> cb.isFalse(root.get("isVoided"));
    }

    private static Specification<Encounter> withEncounterTypeId(Long id) {
        return (root, q, cb) -> cb.equal(root.get("encounterType").get("id"), id);
    }

    private static Specification<Encounter> withStatus(EncounterStatus status) {
        return switch (status) {
            case ALL -> (root, q, cb) -> cb.conjunction();
            case COMPLETED -> (root, q, cb) -> cb.isNotNull(root.get("encounterDateTime"));
            case SCHEDULED -> (root, q, cb) -> cb.and(
                    cb.isNull(root.get("encounterDateTime")),
                    cb.isNull(root.get("cancelDateTime")),
                    cb.isNotNull(root.get("earliestVisitDateTime"))
            );
        };
    }

    private static Specification<Encounter> withSubjectInLocations(List<Long> ids) {
        if (ids == null) return (root, q, cb) -> cb.conjunction();
        return (root, q, cb) -> root.get("individual").get("addressLevel").get("id").in(ids);
    }

    private static Pageable capSize(Pageable in) {
        if (in.getPageSize() <= MAX_PAGE_SIZE) return in;
        return PageRequest.of(in.getPageNumber(), MAX_PAGE_SIZE, in.getSort());
    }
}
