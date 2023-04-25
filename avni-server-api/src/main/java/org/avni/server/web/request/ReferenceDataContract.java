package org.avni.server.web.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.domain.CHSEntity;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ReferenceDataContract extends CHSRequest {
    private String name;

    public ReferenceDataContract() {
    }

    public ReferenceDataContract(String uuid, String name) {
        super(uuid);
        this.name = name;
    }

    public ReferenceDataContract(String uuid) {
        super(uuid);
    }

    public ReferenceDataContract(CHSEntity chsEntity) {
        super(chsEntity);
    }

    public String getName() {
        return name == null ? null : name.trim();
    }

    public void setName(String name) {
        this.name = name;
    }
}
