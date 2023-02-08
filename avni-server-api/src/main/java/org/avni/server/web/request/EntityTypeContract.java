package org.avni.server.web.request;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.avni.server.domain.DeclarativeRule;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Program;

@JsonPropertyOrder({"name", "uuid", "isImmutable"})
public class EntityTypeContract extends ReferenceDataContract {

    private String entityEligibilityCheckRule;
    private Boolean active;
    private DeclarativeRule entityEligibilityCheckDeclarativeRule;
    private Boolean isImmutable;


    public static EntityTypeContract fromEncounterType(EncounterType encounterType) {
        EntityTypeContract contract = new EntityTypeContract();
        contract.setName(encounterType.getName());
        contract.setUuid(encounterType.getUuid());
        contract.setEntityEligibilityCheckRule(encounterType.getEncounterEligibilityCheckRule());
        contract.setVoided(encounterType.isVoided());
        contract.setActive(encounterType.getActive());
        contract.setEntityEligibilityCheckDeclarativeRule(encounterType.getEncounterEligibilityCheckDeclarativeRule());
        contract.setImmutable(encounterType.isImmutable());
        return contract;
    }

    public static EntityTypeContract fromProgram(Program program) {
        EntityTypeContract contract = new EntityTypeContract();
        contract.setName(program.getName());
        contract.setUuid(program.getUuid());
        contract.setEntityEligibilityCheckRule(program.getEnrolmentEligibilityCheckRule());
        contract.setVoided(program.isVoided());
        contract.setActive(program.getActive());
        contract.setEntityEligibilityCheckDeclarativeRule(program.getEnrolmentEligibilityCheckDeclarativeRule());
        return contract;
    }

    public String getEntityEligibilityCheckRule() {
        return entityEligibilityCheckRule;
    }

    public void setEntityEligibilityCheckRule(String entityEligibilityCheckRule) {
        this.entityEligibilityCheckRule = entityEligibilityCheckRule;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public DeclarativeRule getEntityEligibilityCheckDeclarativeRule() {
        return entityEligibilityCheckDeclarativeRule;
    }

    public void setEntityEligibilityCheckDeclarativeRule(DeclarativeRule entityEligibilityCheckDeclarativeRule) {
        this.entityEligibilityCheckDeclarativeRule = entityEligibilityCheckDeclarativeRule;
    }
    public void setImmutable(Boolean immutable) {
        isImmutable = immutable;
    }

    public Boolean getImmutable() {
        return isImmutable;
    }

}
