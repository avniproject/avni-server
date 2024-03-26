package org.avni.server.domain;

import org.joda.time.DateTime;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class OrganisationAwareEntityTest {


    @Test
    public void test_getLastModified(){
        User user1 = new User();
        user1.setUsername("user1");
        user1.setName("user1");

        User user2 = new User();
        user2.setUsername("user2");
        user2.setName("user2");

        SubjectType subjectType = new SubjectType();
        subjectType.setLastModifiedBy(user1);
        subjectType.setCreatedBy(user1);
        subjectType.setName("abc");
        subjectType.setLastModifiedDateTime(new DateTime("2023-05-06T10:11:12.123"));

        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.setCreatedBy(user2);
        operationalSubjectType.setLastModifiedBy(user2);
        operationalSubjectType.setLastModifiedDateTime(new DateTime("2023-05-05T10:11:12.123"));

        Set<OperationalSubjectType> operationalSubjectTypeSet = new HashSet<>();
        operationalSubjectTypeSet.add(operationalSubjectType);
        subjectType.setOperationalSubjectTypes(operationalSubjectTypeSet);
        operationalSubjectType.setSubjectType(subjectType);

        assertEquals(operationalSubjectType.getLastModifiedBy(),user1);
        assertEquals(operationalSubjectType.getLastModifiedDateTime(),new DateTime("2023-05-06T10:11:12.123"));

    }
}
