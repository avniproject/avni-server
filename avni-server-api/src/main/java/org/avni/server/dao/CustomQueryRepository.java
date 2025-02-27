package org.avni.server.dao;

import org.avni.server.domain.CustomQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomQueryRepository extends CHSRepository<CustomQuery> {
    CustomQuery findAllByName(String name);
    List<CustomQuery> findByOrganisationId(Long id);
}
