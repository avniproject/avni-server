package org.avni.server.dao;

import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.VirtualCatchment;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VirtualCatchmentRepository extends AvniJpaRepository<VirtualCatchment, Long> {
    List<VirtualCatchment> findByCatchment(Catchment catchment);
    boolean existsByAddressLevelAndCatchment(AddressLevel addressLevel, Catchment catchment);
}
