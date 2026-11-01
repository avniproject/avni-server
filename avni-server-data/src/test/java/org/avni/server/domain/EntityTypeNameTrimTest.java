package org.avni.server.domain;

import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * avni-client#1635: a subject type, programme or encounter type name saved as " Malaria " never matches "Malaria" in
 * a rule. Every save path (web app, bundle, CSV/metadata upload) sets the name through these setters.
 */
class EntityTypeNameTrimTest {
    private static <T> void assertTrims(Supplier<T> create, BiConsumer<T, String> setName, Function<T, String> getName) {
        T entity = create.get();
        setName.accept(entity, "  Malaria \t");
        assertEquals("Malaria", getName.apply(entity));
        setName.accept(entity, "Ante  Natal");
        assertEquals("Ante  Natal", getName.apply(entity), "only the ends are trimmed");
        setName.accept(entity, null);
        assertNull(getName.apply(entity));
    }

    @Test
    void subjectTypeNameIsTrimmed() {
        assertTrims(SubjectType::new, SubjectType::setName, SubjectType::getName);
    }

    @Test
    void programNameIsTrimmed() {
        assertTrims(Program::new, Program::setName, Program::getName);
    }

    @Test
    void encounterTypeNameIsTrimmed() {
        assertTrims(EncounterType::new, EncounterType::setName, EncounterType::getName);
        assertEquals("Visit", EncounterType.create(" Visit ").getName());
    }

    @Test
    void operationalSubjectTypeNameIsTrimmed() {
        assertTrims(OperationalSubjectType::new, OperationalSubjectType::setName, OperationalSubjectType::getName);
    }

    @Test
    void operationalProgramNameIsTrimmed() {
        assertTrims(OperationalProgram::new, OperationalProgram::setName, OperationalProgram::getName);
    }

    @Test
    void operationalEncounterTypeNameIsTrimmed() {
        assertTrims(OperationalEncounterType::new, OperationalEncounterType::setName, OperationalEncounterType::getName);
    }
}
