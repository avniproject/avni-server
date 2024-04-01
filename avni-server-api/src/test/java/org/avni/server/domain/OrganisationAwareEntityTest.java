package org.avni.server.domain;

import org.joda.time.DateTime;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class OrganisationAwareEntityTest {


    @Test
    public void test_getLastModifiedForSubject(){
        User user1 = new User();
        user1.setUsername("user1");
        user1.setName("user1");

        User user2 = new User();
        user2.setUsername("user2");
        user2.setName("user2");

        DateTime lastModifiedDateTime1 = new DateTime("2023-05-06T10:11:12.123");
        DateTime lastModifiedDateTime2 = new DateTime("2023-05-05T10:11:12.123");

        SubjectType subjectType = new SubjectType();
        subjectType.setLastModifiedBy(user1);
        subjectType.setCreatedBy(user1);
        subjectType.setName("abc");
        subjectType.setLastModifiedDateTime(lastModifiedDateTime1);

        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.setCreatedBy(user2);
        operationalSubjectType.setLastModifiedDateTime(lastModifiedDateTime2);
        operationalSubjectType.setLastModifiedBy(user2);

        Set<OperationalSubjectType> operationalSubjectTypeSet = new HashSet<>();
        operationalSubjectTypeSet.add(operationalSubjectType);
        subjectType.setOperationalSubjectTypes(operationalSubjectTypeSet);
        operationalSubjectType.setSubjectType(subjectType);

        assertEquals(operationalSubjectType.getLastModifiedDateTime(),lastModifiedDateTime1);
        assertEquals(operationalSubjectType.getLastModifiedBy(),user1);

        subjectType.setLastModifiedDateTime(lastModifiedDateTime2);
        operationalSubjectType.setLastModifiedDateTime(lastModifiedDateTime1);

        assertEquals(operationalSubjectType.getLastModifiedDateTime(),lastModifiedDateTime1);
        assertEquals(operationalSubjectType.getLastModifiedBy(),user2);

        operationalSubjectType.setLastModifiedDateTime(lastModifiedDateTime2);
        assertEquals(operationalSubjectType.getLastModifiedDateTime(),lastModifiedDateTime2);
        assertEquals(operationalSubjectType.getLastModifiedBy(),user2);

    }

    @Test
    public void test_getLastModifiedForProgram(){
        User user1 = new User();
        user1.setUsername("user1");
        user1.setName("user1");

        User user2 = new User();
        user2.setUsername("user2");
        user2.setName("user2");

        DateTime lastModifiedDateTime1 = new DateTime("2023-05-06T10:11:12.123");
        DateTime lastModifiedDateTime2 = new DateTime("2023-05-05T10:11:12.123");

        Program program = new Program();
        program.setLastModifiedBy(user1);
        program.setCreatedBy(user1);
        program.setName("abc");
        program.setLastModifiedDateTime(lastModifiedDateTime1);

        OperationalProgram operationalProgramType = new OperationalProgram();
        operationalProgramType.setCreatedBy(user2);
        operationalProgramType.setLastModifiedDateTime(lastModifiedDateTime2);
        operationalProgramType.setLastModifiedBy(user2);

        Set<OperationalProgram> operationalProgramTypeSet = new HashSet<>();
        operationalProgramTypeSet.add(operationalProgramType);
        program.setOperationalPrograms(operationalProgramTypeSet);
        operationalProgramType.setProgram(program);

        assertEquals(operationalProgramType.getLastModifiedDateTime(),lastModifiedDateTime1);
        assertEquals(operationalProgramType.getLastModifiedBy(),user1);

        program.setLastModifiedDateTime(lastModifiedDateTime2);
        operationalProgramType.setLastModifiedDateTime(lastModifiedDateTime1);

        assertEquals(operationalProgramType.getLastModifiedDateTime(),lastModifiedDateTime1);
        assertEquals(operationalProgramType.getLastModifiedBy(),user2);

        operationalProgramType.setLastModifiedDateTime(lastModifiedDateTime2);
        assertEquals(operationalProgramType.getLastModifiedDateTime(),lastModifiedDateTime2);
        assertEquals(operationalProgramType.getLastModifiedBy(),user2);

    }

    @Test
    public void test_getLastModifiedForEncounter(){
        User user1 = new User();
        user1.setUsername("user1");
        user1.setName("user1");

        User user2 = new User();
        user2.setUsername("user2");
        user2.setName("user2");

        DateTime lastModifiedDateTime1 = new DateTime("2023-05-06T10:11:12.123");
        DateTime lastModifiedDateTime2 = new DateTime("2023-05-05T10:11:12.123");

        EncounterType encounterType = new EncounterType();
        encounterType.setLastModifiedBy(user1);
        encounterType.setCreatedBy(user1);
        encounterType.setName("abc");
        encounterType.setLastModifiedDateTime(lastModifiedDateTime1);

        OperationalEncounterType operationalEncounterType = new OperationalEncounterType();
        operationalEncounterType.setCreatedBy(user2);
        operationalEncounterType.setLastModifiedDateTime(lastModifiedDateTime2);
        operationalEncounterType.setLastModifiedBy(user2);

        Set<OperationalEncounterType> operationalEncounterTypeSet = new HashSet<>();
        operationalEncounterTypeSet.add(operationalEncounterType);
        encounterType.setOperationalEncounterTypes(operationalEncounterTypeSet);
        operationalEncounterType.setEncounterType(encounterType);

        assertEquals(operationalEncounterType.getLastModifiedDateTime(),lastModifiedDateTime1);
        assertEquals(operationalEncounterType.getLastModifiedBy(),user1);

        encounterType.setLastModifiedDateTime(lastModifiedDateTime2);
        operationalEncounterType.setLastModifiedDateTime(lastModifiedDateTime1);

        assertEquals(operationalEncounterType.getLastModifiedDateTime(),lastModifiedDateTime1);
        assertEquals(operationalEncounterType.getLastModifiedBy(),user2);

        operationalEncounterType.setLastModifiedDateTime(lastModifiedDateTime2);
        assertEquals(operationalEncounterType.getLastModifiedDateTime(),lastModifiedDateTime2);
        assertEquals(operationalEncounterType.getLastModifiedBy(),user2);

    }
}
