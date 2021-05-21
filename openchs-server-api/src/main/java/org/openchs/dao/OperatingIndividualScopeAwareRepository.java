package org.openchs.dao;

import org.openchs.domain.CHSEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface OperatingIndividualScopeAwareRepository<T extends CHSEntity> extends JpaSpecificationExecutor<T> {
    Page<T> syncByCatchment(SyncParameters syncParameters);
    Page<T> syncByFacility(SyncParameters syncParameters);
}
