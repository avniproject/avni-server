package org.avni.server.dao;

import org.avni.server.domain.ReportCard;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "card", path = "card")
public interface CardRepository extends ReferenceDataRepository<ReportCard>, JpaSpecificationExecutor<ReportCard>, EndOfLife1EndpointRepository<ReportCard> {

    @Query("select c.name from ReportCard c where c.isVoided = false")
    List<String> getAllNames();

    List<ReportCard> findAllByIconFileS3KeyNotNull();

    List<ReportCard> findAllByIsVoidedFalseOrderByName();

    List<ReportCard> findByNameIgnoreCaseAndIsVoidedFalse(String name);
}
