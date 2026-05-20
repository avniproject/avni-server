package org.avni.server.dao.impl;

import org.avni.server.domain.AddressLevel;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lookups that span existing domain entities for the Custom Implementation
 * APIs module. Kept narrow: every other read goes through the standard
 * domain repositories (LocationRepository, EncounterRepository, etc.).
 */
public interface CustomImplRepository extends Repository<AddressLevel, Long> {

    /**
     * IDs of the AddressLevel identified by {@code rootUuid} and every
     * non-voided descendant in the hierarchy. Uses the ltree {@code lineage}
     * column for a single indexed subtree lookup.
     */
    @Query(value = """
            SELECT id FROM address_level
             WHERE lineage <@ (SELECT lineage FROM address_level WHERE uuid = :rootUuid)
               AND is_voided = false
            """, nativeQuery = true)
    List<Long> findSubtreeAddressLevelIds(@Param("rootUuid") String rootUuid);
}
