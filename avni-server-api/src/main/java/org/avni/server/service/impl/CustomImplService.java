package org.avni.server.service.impl;

import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.UserRepository;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
    private final CustomImplRepository customImplRepository;
    private final UserRepository userRepository;

    @Autowired
    public CustomImplService(EncounterRepository encounterRepository,
                             EncounterTypeRepository encounterTypeRepository,
                             CustomImplRepository customImplRepository,
                             UserRepository userRepository) {
        this.encounterRepository = encounterRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.customImplRepository = customImplRepository;
        this.userRepository = userRepository;
    }

    public CatchmentLocationsResponse getCatchmentLocationsForCurrentUser() {
        User contextUser = UserContextHolder.getUserContext().getUser();
        if (contextUser == null) return new CatchmentLocationsResponse(List.of(), List.of());

        // Re-fetch in the active transaction so the lazy
        // Catchment.addressLevels collection can initialise.
        User user = userRepository.findById(contextUser.getId()).orElse(null);
        if (user == null) return new CatchmentLocationsResponse(List.of(), List.of());

        Catchment catchment = user.getCatchment();
        if (catchment == null || catchment.getAddressLevels().isEmpty()) {
            return new CatchmentLocationsResponse(List.of(), List.of());
        }

        Set<AddressLevel> visited = new LinkedHashSet<>();
        Set<String> rootUuids = new LinkedHashSet<>();
        for (AddressLevel leaf : catchment.getAddressLevels()) {
            // Walk UP to capture ancestors (and the root for rootUuids).
            AddressLevel cursor = leaf;
            while (cursor != null) {
                visited.add(cursor);
                if (cursor.getParent() == null) rootUuids.add(cursor.getUuid());
                cursor = cursor.getParent();
            }
            // Walk DOWN to capture every non-voided descendant — this is what
            // lets the LocationFilter cascade past a catchment-leaf node (e.g.
            // into CHC / PHC / Sub-center below "district hospital").
            collectDescendants(leaf, visited);
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

    private static void collectDescendants(AddressLevel node, Set<AddressLevel> visited) {
        for (AddressLevel child : node.getSubLocations()) {
            if (child.isVoided() || visited.contains(child)) continue;
            visited.add(child);
            collectDescendants(child, visited);
        }
    }

    public ResponsePage findEncountersWithLocation(
            String encounterTypeName,
            EncounterStatus status,
            String locationUuid,
            String linkedEncounterTypeName,
            String linkedObservationConceptUuid,
            String linkedLocationUuid,
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

        // Linked-observation filter: optional. All three params must be present together.
        // When provided, narrows to encounters whose subject has a non-voided, completed
        // encounter of `linkedEncounterTypeName` with observation `linkedObservationConceptUuid`
        // pointing at an AddressLevel in the subtree rooted at `linkedLocationUuid`.
        EncounterType linkedEncounterType = null;
        List<String> linkedSubtreeUuids = null;
        boolean anyLinkedParam = linkedEncounterTypeName != null
                || linkedObservationConceptUuid != null
                || linkedLocationUuid != null;
        boolean allLinkedParams = linkedEncounterTypeName != null && !linkedEncounterTypeName.isBlank()
                && linkedObservationConceptUuid != null && !linkedObservationConceptUuid.isBlank()
                && linkedLocationUuid != null && !linkedLocationUuid.isBlank();
        if (anyLinkedParam && !allLinkedParams) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "linkedEncounterType, linkedObservationConceptUuid and linkedLocationUuid must all be provided together");
        }
        if (allLinkedParams) {
            linkedEncounterType = encounterTypeRepository.findByName(linkedEncounterTypeName);
            if (linkedEncounterType == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Encounter type '" + linkedEncounterTypeName + "' not found");
            }
            linkedSubtreeUuids = customImplRepository.findSubtreeAddressLevelUuids(linkedLocationUuid);
            if (linkedSubtreeUuids.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Linked location '" + linkedLocationUuid + "' not found");
            }
        }

        Pageable capped = capSizeAndSort(pageable, status);

        Specification<Encounter> spec = Specification
                .where(notVoided())
                .and(withEncounterTypeId(encounterType.getId()))
                .and(withStatus(status))
                .and(withSubjectInLocations(descendantIds))
                .and(withLinkedObservation(linkedEncounterType, linkedObservationConceptUuid, linkedSubtreeUuids));

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

    /**
     * EXISTS subquery: narrow to encounters whose subject also has at least one
     * non-voided, completed encounter of {@code linkedType} whose observation under
     * {@code observationConceptUuid} is an AddressLevel UUID in {@code subtreeUuids}.
     * Designed for Tanuh's "filter Physician Review encounters by the Place of
     * referral on the linked Oral Screening", but takes only data dimensions so
     * other implementations can reuse it.
     */
    private static Specification<Encounter> withLinkedObservation(
            EncounterType linkedType, String observationConceptUuid, List<String> subtreeUuids) {
        if (linkedType == null || observationConceptUuid == null || subtreeUuids == null) {
            return (root, q, cb) -> cb.conjunction();
        }
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

    /**
     * Caps the page size at {@link #MAX_PAGE_SIZE} and, when the caller has
     * not specified an explicit sort, applies a status-appropriate default:
     * completed encounters by most-recent first (so the latest reviews surface
     * at the top), scheduled by earliest visit date (so the soonest-due
     * appear first). Callers can still override via their own Sort.
     */
    private static Pageable capSizeAndSort(Pageable in, EncounterStatus status) {
        Sort effectiveSort = in.getSort().isUnsorted() ? defaultSortFor(status) : in.getSort();
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
