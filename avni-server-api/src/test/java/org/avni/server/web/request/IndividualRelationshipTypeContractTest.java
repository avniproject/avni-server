package org.avni.server.web.request;

import org.avni.server.domain.individualRelationship.IndividualRelation;
import org.avni.server.domain.individualRelationship.IndividualRelationshipType;
import org.junit.Test;

import static org.junit.Assert.*;

public class IndividualRelationshipTypeContractTest {

    @Test
    public void fromEntityShouldCreateRelationshipTypeContractWhenIncomingRelationshipTypeIsVoided() {
        IndividualRelationshipType relationshipType = new IndividualRelationshipType();
        relationshipType.setVoided(true);
        relationshipType.setName("Test Relation");
        relationshipType.setId(1234L);
        relationshipType.assignUUID();
        relationshipType.setIndividualBIsToA(new IndividualRelation());
        relationshipType.setIndividualAIsToB(new IndividualRelation());
        IndividualRelationshipTypeContract individualRelationshipTypeContract = IndividualRelationshipTypeContract.fromEntity(relationshipType);

        assertNotNull(individualRelationshipTypeContract);
        assertTrue(individualRelationshipTypeContract.isVoided());
    }
}