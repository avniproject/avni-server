package org.avni.server.service;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.SubjectType;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ImportServiceImportTypesTest {
    private FormMapping encounterMapping(String encounterTypeName, boolean formVoided, boolean encounterTypeVoided) {
        SubjectType st = new SubjectType();
        st.setName("Person");
        Form form = new Form();
        form.setName(encounterTypeName + " form");
        form.setFormType(FormType.Encounter);
        form.setVoided(formVoided);
        EncounterType et = new EncounterType();
        et.setName(encounterTypeName);
        et.setVoided(encounterTypeVoided);
        FormMapping fm = new FormMapping();
        fm.setSubjectType(st);
        fm.setForm(form);
        fm.setEncounterType(et);
        return fm;
    }

    @Test
    public void voidedFormsAndVoidedEncounterTypesAreNotOfferedEvenWhenTheirMappingIsStillActive() {
        FormMappingRepository formMappingRepository = mock(FormMappingRepository.class);
        SubjectTypeRepository subjectTypeRepository = mock(SubjectTypeRepository.class);
        when(subjectTypeRepository.findAllOperational()).thenReturn(Collections.emptyList());
        when(formMappingRepository.findAllOperational()).thenReturn(List.of(
                encounterMapping("Live", false, false),
                encounterMapping("VoidedForm", true, false),
                encounterMapping("VoidedType", false, true)));
        ImportService service = new ImportService(subjectTypeRepository, formMappingRepository, null, null, null, null, null, null, null, null, null, null);

        Map<String, ImportService.FormMappingInfo> types = service.getImportTypes();

        assertEquals(types.keySet().toString(), 1, types.size());
        assertTrue(types.containsKey("Encounter---Live---Person"));
    }
}
