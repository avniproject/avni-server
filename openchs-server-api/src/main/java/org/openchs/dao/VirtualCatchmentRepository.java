package org.openchs.dao;

import org.openchs.domain.Catchment;
import org.openchs.domain.VirtualCatchment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VirtualCatchmentRepository extends JpaRepository<VirtualCatchment, Long> {
    List<VirtualCatchment> findByCatchment(Catchment catchment);
}
