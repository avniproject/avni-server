package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.security.access.prepost.PreAuthorize;

@NoRepositoryBean
public interface ImplReferenceDataRepository<T extends CHSEntity> extends ReferenceDataRepository<T> {
}
