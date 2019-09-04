package org.openchs.dao;

import org.openchs.domain.Translation;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranslationRepository extends CrudRepository<Translation, Long> {
    Translation findByOrganisationId(Long organisationId);
}
