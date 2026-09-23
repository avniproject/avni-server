package org.avni.server.exporter;

import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Subject, Location or Encounter answer is stored as the referenced record's UUID. Written to the
 * export as it is stored, the column is unreadable. Written as the name alone it becomes readable
 * but stops identifying anything, since two subjects routinely share a name. So the cell carries
 * both, as name(uuid).
 * <p>
 * One of these serves one export job. A row's unresolved UUIDs are loaded in a single query per
 * type, and every name is cached for the rest of the job, so a reference that repeats across rows
 * costs nothing after the first time it is seen.
 */
public class ExportReferenceResolver {
    /**
     * A subject's name routinely contains a comma of its own, so a comma separated list of them
     * cannot be split back apart by whoever reads the file, however correctly the cell is quoted.
     */
    static final String MULTI_VALUE_SEPARATOR = "; ";

    private final IndividualRepository individualRepository;
    private final LocationRepository locationRepository;
    private final EncounterRepository encounterRepository;

    /**
     * The export loop clears its entity manager every chunk to cap memory on a long job, so this
     * cache must not grow without limit. Past the cap it simply stops remembering; a high
     * cardinality column then costs a query per row, which is what it cost before any caching.
     */
    static final int DEFAULT_MAX_CACHED_NAMES_PER_TYPE = 50000;

    private final int maxCachedNamesPerType;
    private final Map<String, Map<String, String>> namesByDataType = new HashMap<>();

    public ExportReferenceResolver(IndividualRepository individualRepository,
                                   LocationRepository locationRepository,
                                   EncounterRepository encounterRepository) {
        this(individualRepository, locationRepository, encounterRepository, DEFAULT_MAX_CACHED_NAMES_PER_TYPE);
    }

    ExportReferenceResolver(IndividualRepository individualRepository,
                            LocationRepository locationRepository,
                            EncounterRepository encounterRepository,
                            int maxCachedNamesPerType) {
        this.maxCachedNamesPerType = maxCachedNamesPerType;
        this.individualRepository = individualRepository;
        this.locationRepository = locationRepository;
        this.encounterRepository = encounterRepository;
    }

    public static boolean isReferenceType(String dataType) {
        return ConceptDataType.matches(dataType, ConceptDataType.Subject, ConceptDataType.Location, ConceptDataType.Encounter);
    }

    /**
     * The display value for one answer, as name(uuid). A multi-select answer holds a list of UUIDs
     * and resolves to one such pair per answer, joined by {@link #MULTI_VALUE_SEPARATOR}. A
     * reference this export cannot see - deleted, voided, or outside the exporting user's
     * visibility - keeps its UUID on its own, so the row still says which record was chosen.
     */
    public String resolve(String dataType, Object value) {
        List<String> uuids = toUuids(value);
        if (uuids.isEmpty()) return "";

        Map<String, String> cached = namesByDataType.computeIfAbsent(dataType, key -> new HashMap<>());
        Map<String, String> justFetched = fetchMissing(dataType, uuids, cached);
        return uuids.stream()
                .map(uuid -> label(justFetched.containsKey(uuid) ? justFetched.get(uuid) : cached.getOrDefault(uuid, ""), uuid))
                .collect(Collectors.joining(MULTI_VALUE_SEPARATOR));
    }

    private static String label(String name, String uuid) {
        return name.isEmpty() ? uuid : name + "(" + uuid + ")";
    }

    /**
     * Fetches whatever this answer needs and is not already known, and remembers it unless the cache
     * is full. Returns only what was fetched now; the caller reads that first and the cache second,
     * so nothing is ever copied and a full cache costs one query for the row rather than a scan.
     */
    private Map<String, String> fetchMissing(String dataType, List<String> uuids, Map<String, String> cached) {
        List<String> missing = uuids.stream().filter(uuid -> !cached.containsKey(uuid)).distinct().collect(Collectors.toList());
        if (missing.isEmpty()) return Collections.emptyMap();

        Map<String, String> fetched = new HashMap<>();
        // A reference this export cannot see is recorded as an empty name, so it is looked up once
        // rather than on every row that carries it.
        missing.forEach(uuid -> fetched.put(uuid, ""));
        fetched.putAll(fetchNames(dataType, missing));

        if (cached.size() + fetched.size() <= maxCachedNamesPerType) cached.putAll(fetched);
        return fetched;
    }

    private Map<String, String> fetchNames(String dataType, List<String> uuids) {
        if (ConceptDataType.matches(ConceptDataType.Subject, dataType)) {
            return individualRepository.findAllByUuidIn(uuids).stream()
                    .collect(Collectors.toMap(Individual::getUuid, ExportReferenceResolver::subjectName));
        }
        if (ConceptDataType.matches(ConceptDataType.Location, dataType)) {
            return locationRepository.findByUuidIn(uuids).stream()
                    .collect(Collectors.toMap(AddressLevel::getUuid, ExportReferenceResolver::locationName));
        }
        if (ConceptDataType.matches(ConceptDataType.Encounter, dataType)) {
            return encounterRepository.findAllByUuidIn(uuids).stream()
                    .collect(Collectors.toMap(Encounter::getUuid, ExportReferenceResolver::encounterName));
        }
        return Collections.emptyMap();
    }

    private static String subjectName(Individual individual) {
        return blankIfNull(individual.getFullName());
    }

    private static String locationName(AddressLevel addressLevel) {
        return blankIfNull(addressLevel.getTitle());
    }

    /**
     * A general encounter usually carries no name of its own, in which case the encounter type is
     * the only label a reader would recognise. Two visits of the same type read alike, which is
     * why the UUID travels with it.
     */
    private static String encounterName(Encounter encounter) {
        if (encounter.getName() != null && !encounter.getName().isEmpty()) return encounter.getName();
        return encounter.getEncounterType() == null ? "" : blankIfNull(encounter.getEncounterType().getName());
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toUuids(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof Collection) {
            List<String> uuids = new ArrayList<>();
            ((Collection<Object>) value).stream()
                    .filter(each -> each instanceof String)
                    .forEach(each -> uuids.add((String) each));
            return uuids;
        }
        return value instanceof String ? Collections.singletonList((String) value) : Collections.emptyList();
    }
}
