package org.avni.server.framework.hibernate;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers Postgres-specific SQL functions for use from JPA Criteria / HQL.
 *
 * <p><b>jsonb_contains_value(jsonb, text)</b> &rarr; renders the Postgres jsonb containment
 * <b>operator</b> {@code (?1 @> cast(?2 as jsonb))}. It is intentionally emitted as the
 * {@code @>} operator (not as a function call) so the planner can use a
 * {@code GIN (observations jsonb_path_ops)} index — a {@code jsonb_contains(a, b)} function
 * call would NOT be matched to the GIN operator class. Used by
 * {@code CHSRepository.withConceptValues} for indexed concept-value lookups (see #1005).
 */
public class AvniSqlFunctionContributor implements FunctionContributor {
    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry()
                .patternDescriptorBuilder("jsonb_contains_value", "(?1 @> cast(?2 as jsonb))")
                .setInvariantType(functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry().resolve(StandardBasicTypes.BOOLEAN))
                .setExactArgumentCount(2)
                .register();
    }
}
