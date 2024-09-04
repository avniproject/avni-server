package org.avni.server.domain.accessControl;

import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Individual;
import org.springframework.util.StringUtils;

public class SubjectPartitionData {
    private final AddressLevel addressLevel;
    private final String sync1ConceptValue;
    private final String sync2ConceptValue;

    private SubjectPartitionData(AddressLevel addressLevel, String sync1ConceptValue, String sync2ConceptValue) {
        if (addressLevel == null && StringUtils.isEmpty(sync1ConceptValue)) {
            throw new IllegalArgumentException("AddressLevel or sync1ConceptValue must be provided");
        }
        this.addressLevel = addressLevel;
        this.sync1ConceptValue = sync1ConceptValue;
        this.sync2ConceptValue = sync2ConceptValue;
    }

    public static SubjectPartitionData create(Individual subject) {
        if (subject == null) return null;
        return new SubjectPartitionData(subject.getAddressLevel(), subject.getSyncConcept1Value(), subject.getSyncConcept2Value());
    }

    public AddressLevel getAddressLevel() {
        return addressLevel;
    }

    public String getSync1ConceptValue() {
        return sync1ConceptValue;
    }

    public String getSync2ConceptValue() {
        return sync2ConceptValue;
    }
}
