package org.avni.server.dao;

import org.avni.server.domain.CustomQuery;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomQueryRepository extends CHSRepository<CustomQuery> {
    CustomQuery findAllByName(String name);
}
