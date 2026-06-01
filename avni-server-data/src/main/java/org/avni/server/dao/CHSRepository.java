package org.avni.server.dao;

import jakarta.persistence.criteria.*;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.util.ObjectMapperSingleton;
import org.joda.time.DateTime;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.NoRepositoryBean;

import jakarta.persistence.criteria.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@NoRepositoryBean
public interface CHSRepository<T extends CHSEntity> extends AvniCrudRepository<T, Long> {
    T findByUuid(String uuid);
    List<T> findAll();
    List<T> findAllByIsVoidedFalse();

    default T findEntity(Long id) {
        if (id == null) return null;
        return findById(id).orElse(null);
    }

    default T findEntity(String uuid) {
        if (uuid == null) return null;
        return findByUuid(uuid);
    }

    default Predicate jsonContains(Path<?> jsonb, String pattern, CriteriaBuilder builder) {
        return builder.isTrue(builder.function("jsonb_object_values_contain", Boolean.class,
                jsonb, builder.literal(pattern)));
    }

    default Expression<String> jsonExtractPathText(Path<?> jsonb, String key, CriteriaBuilder builder) {
        return builder.function("jsonb_extract_path_text",
                String.class,
                jsonb,
                builder.literal(key)
        );
    }

    default Expression<String> convertToDate(Path<?> path, CriteriaBuilder cb) {
        return cb.function("TO_CHAR", String.class, path, cb.literal("yyyy-MM-dd"));
    }

    default Specification lastModifiedBetween(Date lastModifiedDateTime, Date now) {
        Specification<T> spec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (lastModifiedDateTime != null) {
                predicates.add(cb.greaterThan(root.get("lastModifiedDateTime"), cb.literal(lastModifiedDateTime)));
                predicates.add(cb.lessThan(root.get("lastModifiedDateTime"), cb.literal(now)));
                query.orderBy(cb.asc(root.get("lastModifiedDateTime")), cb.asc(root.get("id")));
            }

            return cb.and(predicates.toArray(new Predicate[predicates.size()]));
        };
        return spec;
    }

    default Specification withConceptValues(Map<Concept, String> concepts, String observationField) {
        Specification<T> spec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            concepts.forEach((concept, value) -> {
                predicates.add(conceptValuePredicate(root.get(observationField), concept, value, cb));
            });

            return cb.and(predicates.toArray(new Predicate[predicates.size()]));
        };
        return spec;
    }

    /**
     * Predicate matching a single observation key=value.
     *
     * <p>For string-valued concepts (text, coded answer-uuid, id, date-as-text, …) this emits the
     * jsonb containment operator {@code observations @> {"<uuid>":"<value>"}} via
     * {@code jsonb_contains_value}, so a {@code GIN(observations jsonb_path_ops)} index can serve the
     * lookup instead of a full-table scan (#1005). Containment on a {@code {"uuid":"value"}} object is
     * equivalent to the previous {@code jsonb_extract_path_text(...) = value} for scalar string values,
     * and (like before) does not match array/object-valued observations such as multi-select coded.
     *
     * <p>Numeric concepts are stored as JSON numbers, so the scalar-string containment above would not
     * match them — those keep the original {@code jsonb_extract_path_text} text-equality (uncommon in this
     * filter; not index-accelerated). A null value also falls back to the original behaviour.
     */
    default Predicate conceptValuePredicate(Path<?> observations, Concept concept, String value, CriteriaBuilder cb) {
        boolean numeric = ConceptDataType.Numeric.toString().equals(concept.getDataType());
        if (numeric || value == null) {
            return cb.equal(jsonExtractPathText(observations, concept.getUuid(), cb), value);
        }
        String containment = ObjectMapperSingleton.writeValueAsStringSafe(Map.of(concept.getUuid(), value));
        return cb.isTrue(cb.function("jsonb_contains_value", Boolean.class, observations, cb.literal(containment)));
    }

    default void voidEntity(Long id) {
        T entity = this.findEntity(id);
        entity.setVoided(true);
        this.save(entity);
    }

    boolean existsByLastModifiedDateTimeGreaterThan(Date lastModifiedDateTime);

    default boolean existsByLastModifiedDateTimeGreaterThan(DateTime lastModifiedDateTime) {
        return existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime == null ? null : lastModifiedDateTime.toDate());
    }
}
