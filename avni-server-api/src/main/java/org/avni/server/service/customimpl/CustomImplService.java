package org.avni.server.service.customimpl;

import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.LocationService;
import org.avni.server.web.request.customimpl.EncounterSearchRequest;
import org.avni.server.web.response.ResponsePage;
import org.avni.server.web.response.customimpl.CatchmentLocationNode;
import org.avni.server.web.response.customimpl.CatchmentLocationsResponse;
import org.avni.server.web.response.customimpl.EncounterWithLocationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomImplService {
    private static final int MAX_PAGE_SIZE = 200;

    private final EncounterRepository encounterRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final LocationService locationService;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    @Autowired
    public CustomImplService(EncounterRepository encounterRepository,
                             EncounterTypeRepository encounterTypeRepository,
                             LocationService locationService,
                             LocationRepository locationRepository,
                             UserRepository userRepository) {
        this.encounterRepository = encounterRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.locationService = locationService;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
    }

    public CatchmentLocationsResponse getCatchmentLocationsForCurrentUser() {
        User contextUser = UserContextHolder.getUserContext().getUser();
        if (contextUser == null) return new CatchmentLocationsResponse(List.of(), List.of());

        User user = userRepository.findById(contextUser.getId()).orElse(null);
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
            // Descend past catchment leaves so the client-side LocationFilter
            // can drill into sub-levels that aren't themselves catchment nodes.
            collectDescendants(leaf, visited);
        }

        List<CatchmentLocationNode> nodes = visited.stream()
                .filter(al -> !al.isVoided())
                .map(al -> new CatchmentLocationNode(
                        al.getUuid(),
                        al.getTitle(),
                        al.getType() == null ? null : al.getType().getName(),
                        al.getParent() == null ? null : al.getParent().getUuid()))
                .collect(Collectors.toList());
        return new CatchmentLocationsResponse(nodes, List.copyOf(rootUuids));
    }

    private static void collectDescendants(AddressLevel node, Set<AddressLevel> visited) {
        for (AddressLevel child : node.getNonVoidedSubLocations()) {
            if (!visited.add(child)) continue;
            collectDescendants(child, visited);
        }
    }

    public ResponsePage findEncountersWithLocation(EncounterSearchRequest request, Pageable pageable) {
        EncounterStatus status = EncounterStatus.from(request.status());

        if (!StringUtils.hasText(request.encounterType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "encounterType is required");
        }
        EncounterType encounterType = encounterTypeRepository.findByName(request.encounterType());
        if (encounterType == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Encounter type '" + request.encounterType() + "' not found");
        }

        List<Long> descendantIds = null;
        if (StringUtils.hasText(request.locationUuid())) {
            requireLocationExists(request.locationUuid());
            descendantIds = locationService.getAllWithChildrenForUUIDs(List.of(request.locationUuid()));
        }

        // All three linked-* params must be supplied together (or none).
        EncounterType linkedEncounterType = null;
        List<String> linkedSubtreeUuids = null;
        boolean anyLinkedParam = request.linkedEncounterType() != null
                || request.linkedObservationConceptUuid() != null
                || request.linkedLocationUuid() != null;
        boolean allLinkedParams = StringUtils.hasText(request.linkedEncounterType())
                && StringUtils.hasText(request.linkedObservationConceptUuid())
                && StringUtils.hasText(request.linkedLocationUuid());
        if (anyLinkedParam && !allLinkedParams) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "linkedEncounterType, linkedObservationConceptUuid and linkedLocationUuid must all be provided together");
        }
        if (allLinkedParams) {
            linkedEncounterType = encounterTypeRepository.findByName(request.linkedEncounterType());
            if (linkedEncounterType == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Encounter type '" + request.linkedEncounterType() + "' not found");
            }
            linkedSubtreeUuids = findSubtreeUuidsOrThrow(request.linkedLocationUuid(), "Linked location");
        }

        Pageable capped = capSizeAndSort(pageable, status);

        Specification<Encounter> spec = Specification
                .where(notVoided())
                .and(withEncounterTypeId(encounterType.getId()))
                .and(withStatus(status))
                .and(withSubjectInLocations(descendantIds))
                .and(withLinkedObservation(linkedEncounterType, request.linkedObservationConceptUuid(), linkedSubtreeUuids));

        Page<Encounter> page = encounterRepository.findAll(spec, capped);
        List<EncounterWithLocationResponse> content = page.getContent().stream()
                .map(EncounterWithLocationResponse::from)
                .collect(Collectors.toList());
        return new ResponsePage(content,
                (int) page.getTotalElements(),
                page.getTotalPages(),
                page.getSize());
    }

    private void requireLocationExists(String uuid) {
        if (locationRepository.findByUuid(uuid) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Location '" + uuid + "' not found");
        }
    }

    private List<String> findSubtreeUuidsOrThrow(String rootUuid, String label) {
        AddressLevel root = locationRepository.findByUuid(rootUuid);
        if (root == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    label + " '" + rootUuid + "' not found");
        }
        String lquery = "*." + root.getLineage() + ".*";
        return locationRepository.getAllChildLocations(lquery).stream()
                .filter(al -> !al.isVoided())
                .map(AddressLevel::getUuid)
                .collect(Collectors.toList());
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
        if (ids.isEmpty()) return (root, q, cb) -> cb.disjunction();
        return (root, q, cb) -> root.get("individual").get("addressLevel").get("id").in(ids);
    }

    private static Specification<Encounter> withLinkedObservation(
            EncounterType linkedType, String observationConceptUuid, List<String> subtreeUuids) {
        if (linkedType == null || observationConceptUuid == null || subtreeUuids == null) {
            return (root, q, cb) -> cb.conjunction();
        }
        if (subtreeUuids.isEmpty()) return (root, q, cb) -> cb.disjunction();
        return (root, q, cb) -> {
            Subquery<Long> sub = q.subquery(Long.class);
            Root<Encounter> linked = sub.from(Encounter.class);
            sub.select(cb.literal(1L)).where(
                    cb.equal(linked.get("individual"), root.get("individual")),
                    cb.equal(linked.get("encounterType").get("id"), linkedType.getId()),
                    cb.isFalse(linked.get("isVoided")),
                    cb.isNotNull(linked.get("encounterDateTime")),
                    cb.function(
                            "jsonb_extract_path_text",
                            String.class,
                            linked.get("observations"),
                            cb.literal(observationConceptUuid)
                    ).in(subtreeUuids)
            );
            return cb.exists(sub);
        };
    }

    private static Pageable capSizeAndSort(Pageable in, EncounterStatus status) {
        Sort base = in.getSort().isUnsorted() ? defaultSortFor(status) : in.getSort();
        // Append id as a tiebreaker so pagination is deterministic under
        // concurrent writes and on rows that tie on the primary sort column.
        Sort effectiveSort = base.and(Sort.by(Sort.Direction.DESC, "id"));
        int size = Math.min(in.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(in.getPageNumber(), size, effectiveSort);
    }

    private static Sort defaultSortFor(EncounterStatus status) {
        return switch (status) {
            case COMPLETED -> Sort.by("encounterDateTime").descending();
            case SCHEDULED -> Sort.by("earliestVisitDateTime").ascending();
            case ALL -> Sort.unsorted();
        };
    }
}
