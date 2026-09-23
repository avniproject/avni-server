package org.avni.server.exporter;

import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.util.DateTimeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A Subject, Location or Encounter answer is stored as the referenced record's UUID. Written to the
 * export as it is stored, the column is unreadable and cannot be joined to anything, so resolve it
 * to a readable name. The app labels these from a configurable template that the server does
 * not evaluate, so this is the record's own name rather than that label.
 * <p>
 * One of these serves one export job. A row's unresolved UUIDs are loaded in a single query per
 * type, and every name is cached for the rest of the job, so a reference that repeats across rows
 * costs nothing after the first time it is seen.
 */
public class ExportReferenceResolver {
    /**
     * A subject's name routinely contains a comma, so a list of them cannot be comma separated
     * without depending on the caller quoting the cell correctly.
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
    static final int MAX_CACHED_NAMES_PER_TYPE = 50000;

    private final String timeZone;
    private final Map<String, Map<String, String>> namesByDataType = new HashMap<>();

    public ExportReferenceResolver(IndividualRepository individualRepository,
                                   LocationRepository locationRepository,
                                   EncounterRepository encounterRepository,
                                   String timeZone) {
        this.individualRepository = individualRepository;
        this.locationRepository = locationRepository;
        this.encounterRepository = encounterRepository;
        this.timeZone = timeZone;
    }

    public static boolean isReferenceType(String dataType) {
        return ConceptDataType.matches(dataType, ConceptDataType.Subject, ConceptDataType.Location, ConceptDataType.Encounter);
    }

    /**
     * The display value for one answer. A multi-select answer holds a list of UUIDs and resolves to
     * the names joined by {@link #MULTI_VALUE_SEPARATOR}. A reference this export cannot see -
     * deleted, voided, or outside the exporting user's visibility - resolves to nothing rather than
     * falling back to the UUID, which would put an unreadable value back in a column the rest of
     * which reads as names.
     */
    public String resolve(String dataType, Object value) {
        List<String> uuids = toUuids(value);
        if (uuids.isEmpty()) return "";

        Map<String, String> names = load(dataType, uuids);
        return uuids.stream()
                .map(uuid -> names.getOrDefault(uuid, ""))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining(MULTI_VALUE_SEPARATOR));
    }

    private Map<String, String> load(String dataType, List<String> uuids) {
        Map<String, String> names = namesByDataType.computeIfAbsent(dataType, key -> new HashMap<>());
        List<String> missing = uuids.stream().filter(uuid -> !names.containsKey(uuid)).distinct().collect(Collectors.toList());
        if (missing.isEmpty()) return names;

        Map<String, String> fetched = fetchNames(dataType, missing);
        if (names.size() + missing.size() > MAX_CACHED_NAMES_PER_TYPE) {
            Map<String, String> thisRowOnly = new HashMap<>(names);
            missing.forEach(uuid -> thisRowOnly.put(uuid, ""));
            thisRowOnly.putAll(fetched);
            return thisRowOnly;
        }
        // A miss is cached as an empty name so a reference this export cannot see is looked up once.
        missing.forEach(uuid -> names.put(uuid, ""));
        fetched.forEach(names::put);
        return names;
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
                    .collect(Collectors.toMap(Encounter::getUuid, this::encounterName));
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
     * A general encounter usually carries no name of its own. Falling back to the type alone would
     * render three visits of the same type as the same word three times, which is less use than the
     * UUID it replaces, so the visit date goes with it.
     */
    private String encounterName(Encounter encounter) {
        if (encounter.getName() != null && !encounter.getName().isEmpty()) return encounter.getName();
        String typeName = encounter.getEncounterType() == null ? "" : blankIfNull(encounter.getEncounterType().getName());
        String visitDate = encounter.getEncounterDateTime() == null ? ""
                : DateTimeUtil.getDateForTimeZone(encounter.getEncounterDateTime(), timeZone).toLocalDate().toString();
        return Stream.of(typeName, visitDate).filter(part -> !part.isEmpty()).collect(Collectors.joining(" "));
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
