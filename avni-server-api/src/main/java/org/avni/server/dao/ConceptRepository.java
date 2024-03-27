package org.avni.server.dao;

import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.response.ConceptNameUuidAndDatatype;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RepositoryRestResource(collectionResourceRel = "concept", path = "concept")
public interface ConceptRepository extends ReferenceDataRepository<Concept>, FindByLastModifiedDateTime<Concept> {
    @QueryHints({@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_CACHEABLE, value = "true")})
    @Query("select c from Concept c where c.name = ?1 and c.organisationId IN ?2")
    Concept findByNameAndOrganisationId(String name, List<Long> organisationIds);

    default List<Long> buildOrganisationIdList() {
        OrganisationRepository organisationRepository = RepositoryProvider.getOrganisationRepository();
        List<Long> organisationIds = new ArrayList<>();
        UserContext userContext = UserContextHolder.getUserContext();
        organisationIds.add(userContext.getOrganisationId());
        addOrganisation(organisationIds, userContext.getOrganisation(), organisationRepository);
        return organisationIds;
    }

    default void addOrganisation(List<Long> organisationIds, Organisation organisation, OrganisationRepository organisationRepository) {
        if (organisation.getParentOrganisationId() != null) {
            organisationIds.add(organisation.getParentOrganisationId());
            Organisation parentOrganisation = organisationRepository.findOne(organisation.getParentOrganisationId());
            addOrganisation(organisationIds, parentOrganisation, organisationRepository);
        }
    }

    @Override
    default Concept findByName(String name) {
        return this.findByNameAndOrganisationId(name, buildOrganisationIdList());
    }

    Page<Concept> findByIsVoidedFalseAndNameIgnoreCaseContaining(String name, Pageable pageable);
    List<Concept> findAllByDataType(String dataType);
    List<Concept> findAllByDataTypeInAndIsVoidedFalse(List<String> conceptDataTypes);
    List<Concept> findByIsVoidedFalseAndDataType(String dataType);

    List<Concept> findByIsVoidedFalseAndActiveTrueAndNameIgnoreCaseContains(String name);
    List<Concept> findByIsVoidedFalseAndActiveTrueAndDataTypeAndNameIgnoreCaseContains(String dataType, String name);

    @QueryHints({@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_CACHEABLE, value = "true")})
    @Query("select c from Concept c where c.uuid = ?1 and c.organisationId IN ?2")
    Concept findByUuidAndOrganisationId(String uuid, List<Long> organisationIds);

    @Override
    default Concept findByUuid(String uuid) {
        return this.findByUuidAndOrganisationId(uuid, buildOrganisationIdList());
    }

    @Query("select c from Concept c where c.isVoided = false")
    Page<Concept> getAllNonVoidedConcepts(Pageable pageable);

    @Query("select c.name from Concept c where c.isVoided = false")
    List<String> getAllNames();
    List<Concept> getAllConceptByUuidIn(List<String> uuid);

    Page<Concept> findAllByUuidIn(String [] uuids, Pageable pageable);
    List<Concept> findAllByUuidInAndDataTypeIn(String[] uuids, String[] dataTypes);

    List<Concept> findByIsVoidedFalseAndNameIgnoreCaseContainsAndDataTypeIn(String name, List<String> includedDataTypes);

    default List<Concept> findDashboardFilterConcepts(String namePart) {
        List<String> supportedDataTypes = ConceptDataType.dashboardFilterSupportedTypes.stream().map(Enum::name).collect(Collectors.toList());
        return findByIsVoidedFalseAndNameIgnoreCaseContainsAndDataTypeIn(namePart, supportedDataTypes);
    }
}
