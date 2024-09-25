package org.avni.server.service;

import org.avni.server.application.projections.VirtualCatchmentProjection;

public class VirtualCatchmentProjectTestImplementation implements VirtualCatchmentProjection {
    private final Long id;
    private final Long addressLevel_id;
    private final Long catchment_id;
    private final Long type_id;

    public VirtualCatchmentProjectTestImplementation(Long id, Long addressLevel_id, Long catchment_id, Long type_id) {
        this.id = id;
        this.addressLevel_id = addressLevel_id;
        this.catchment_id = catchment_id;
        this.type_id = type_id;
    }

    @Override
    public Long getId() {
        return this.id;
    }

    @Override
    public Long getAddresslevel_id() {
        return this.addressLevel_id;
    }

    @Override
    public Long getCatchment_id() {
        return this.catchment_id;
    }

    @Override
    public Long getType_id() {
        return this.type_id;
    }
}
