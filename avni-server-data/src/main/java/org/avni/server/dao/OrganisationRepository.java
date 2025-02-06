package org.avni.server.dao;

import org.avni.server.domain.Organisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "organisation", path = "organisation")
public interface OrganisationRepository extends AvniCrudRepository<Organisation, Long>, JpaSpecificationExecutor<Organisation> {
    Organisation findByName(String name);

    Organisation findByUuid(String organisationUuid);

    default Organisation findOne(Long organisationId) {
        return findById(organisationId).orElse(null);
    }

    List<Organisation> findAllByIsVoidedFalse();

    List<Organisation> findByAccount_AccountAdmin_User_Id(Long userId);

    Organisation findByIdAndAccount_AccountAdmin_User_Id(Long id, Long userId);

    Page<Organisation> findAllByIdInAndIsVoidedFalse(Long[] ids, Pageable pageable);
}
