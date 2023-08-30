package org.avni.server.dao;

import org.avni.server.domain.AccountAdmin;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "accountAdmin", path = "accountAdmin")
public interface AccountAdminRepository extends AvniCrudRepository<AccountAdmin, Long> {

    AccountAdmin findByUser_IdAndAccount_Id(Long userId, Long accountId);

    List<AccountAdmin> findByUser_Id(Long userId);
}
