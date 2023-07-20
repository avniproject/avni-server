package org.avni.server.dao;

import org.avni.server.domain.EncounterType;
import org.avni.server.domain.EncounterType.EncounterTypeProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "encounterType", path = "encounterType")
public interface EncounterTypeRepository extends ReferenceDataRepository<EncounterType>, FindByLastModifiedDateTime<EncounterType> {

    @Query("select o from EncounterType o where o.operationalEncounterTypes is not empty and o.isVoided = false")
    List<EncounterTypeProjection> findAllOperational();

    @Query("select e.name from EncounterType e where e.isVoided = false")
    List<String> getAllNames();

    @Query("select e.name from EncounterType e where e.isVoided = false and e.uuid =:EncounterTypeUUID")
    String getEncounterTypeName(String EncounterTypeUUID);
}
