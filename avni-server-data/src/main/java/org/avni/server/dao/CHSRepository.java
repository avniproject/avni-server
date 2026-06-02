package org.avni.server.dao;

import jakarta.persistence.criteria.*;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.Concept;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
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
                predicates.add(cb.equal(jsonExtractPathText(root.get(observationField), concept.getUuid(), cb), value));
            });

            return cb.and(predicates.toArray(new Predicate[predicates.size()]));
        };
        return spec;
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

    /**
     * Restricts a query to the current organisation via a standalone {@code organisation_id = ?} predicate.
     *
     * <p>The RLS policy on transactional tables is {@code organisation_id = <own org> OR organisation_id IN
     * (organisation_group_organisation)}; the {@code OR}-with-subplan is not index-pushable, so the planner
     * ignores the organisation_id btree and full-scans the whole (cross-org) table. Adding this flat,
     * indexable equality lets the planner restrict to the org's rows first (see #1005 follow-up). RLS still
     * applies as the backstop. Intended for single-organisation read paths (the external /api endpoints).
     */
    default Specification<T> inCurrentOrganisation() {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            UserContext userContext = UserContextHolder.getUserContext();
            Long organisationId = userContext == null ? null : userContext.getOrganisationId();
            return organisationId == null ? cb.conjunction() : cb.equal(root.get("organisationId"), organisationId);
        };
    }
}
