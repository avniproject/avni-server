package org.avni.server.dao;

import org.avni.server.domain.User;
import org.avni.server.domain.UserSubject;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSubjectRepository extends AvniJpaRepository<UserSubject, Long> {
    UserSubject findByUser(User user);
}
