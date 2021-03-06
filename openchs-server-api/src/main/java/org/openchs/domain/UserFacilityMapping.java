package org.openchs.domain;

import org.hibernate.annotations.BatchSize;
import org.openchs.application.projections.BaseProjection;
import org.springframework.data.rest.core.config.Projection;

import javax.persistence.*;

@Entity
@Table(name = "user_facility_mapping")
@BatchSize(size = 100)
public class UserFacilityMapping extends OrganisationAwareEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "facility_id")
    private Facility facility;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    public Facility getFacility() {
        return facility;
    }

    public void setFacility(Facility facility) {
        this.facility = facility;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Projection(name = "UserFacilityMappingProjection", types = {UserFacilityMapping.class})
    public interface UserFacilityMappingProjection extends BaseProjection {
        Facility getFacility();
    }

}
