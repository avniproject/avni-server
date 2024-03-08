package org.avni.server.dao;

import org.avni.server.domain.Card;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "card", path = "card")
public interface CardRepository extends ReferenceDataRepository<Card>, FindByLastModifiedDateTime<Card>, JpaSpecificationExecutor<Card> {

    @Query("select c.name from Card c where c.isVoided = false")
    List<String> getAllNames();

    List<Card> findAllByIconFileS3KeyNotNull();

}
