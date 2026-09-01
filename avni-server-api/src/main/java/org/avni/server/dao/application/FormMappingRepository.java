package org.avni.server.dao.application;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.FindByLastModifiedDateTime;
import org.avni.server.dao.ReferenceDataRepository;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "formMapping", path = "formMapping")
public interface FormMappingRepository extends ReferenceDataRepository<FormMapping>, FindByLastModifiedDateTime<FormMapping> {
    Page<FormMapping> findByProgramIdAndImplVersion(Long programId, int implVersion, Pageable pageable);

    default Page<FormMapping> findByProgramId(Long programId, Pageable pageable) {
        return this.findByProgramIdAndImplVersion(programId, FormMapping.IMPL_VERSION, pageable);
    }

    List<FormMapping> findByFormIdAndImplVersion(Long formId, int implVersion);

    default List<FormMapping> findByFormId(Long formId) {
        return this.findByFormIdAndImplVersion(formId, FormMapping.IMPL_VERSION);
    }

    FormMapping findFirstByFormAndImplVersion(Form form, int implVersion);

    default FormMapping findFirstByForm(Form form) {
        return this.findFirstByFormAndImplVersion(form, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findByFormIdAndImplVersionAndIsVoidedFalse(Long formId, int implVersion);

    default List<FormMapping> findByFormIdAndIsVoidedFalse(Long formId) {
        return this.findByFormIdAndImplVersionAndIsVoidedFalse(formId, FormMapping.IMPL_VERSION);
    }

    FormMapping findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndImplVersionAndIsVoidedFalse(Long programId, Long encounterTypeId, FormType formType, Long subjectTypeId, int implVersion);

    default FormMapping findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndIsVoidedFalse(Long programId, Long encounterTypeId, FormType formType, Long subjectTypeId) {
        return this.findByProgramIdAndEncounterTypeIdAndFormFormTypeAndSubjectTypeIdAndImplVersionAndIsVoidedFalse(programId, encounterTypeId, formType, subjectTypeId, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findBySubjectTypeAndFormFormTypeAndImplVersionAndIsVoidedFalse(SubjectType subjectType, FormType formType, int implVersion);

    default List<FormMapping> findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(SubjectType subjectType, FormType formType) {
        return this.findBySubjectTypeAndFormFormTypeAndImplVersionAndIsVoidedFalse(subjectType, formType, FormMapping.IMPL_VERSION);
    }

    FormMapping findBySubjectTypeNameAndFormFormTypeAndIsVoidedFalseAndImplVersion(String subjectType, FormType formType, int implVersion);

    default FormMapping findBySubjectTypeNameAndFormFormTypeAndIsVoidedFalse(String subjectType, FormType formType) {
        return this.findBySubjectTypeNameAndFormFormTypeAndIsVoidedFalseAndImplVersion(subjectType, formType, FormMapping.IMPL_VERSION);
    }

    @Query("select m from FormMapping m where m.isVoided = false and m.implVersion = 1")
    List<FormMapping> findAllOperational();

    default FormMapping findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in FormMapping");
    }

    default FormMapping findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in FormMapping");
    }

    //    Registration
    //    The form type filter is required, not cosmetic: without it this returns every non-voided
    //    mapping that has no program and no encounter type, and more than one form type has that
    //    shape (SubjectEnrolmentEligibility, and now Approval/Rejection). Two matches make Spring
    //    Data throw IncorrectResultSizeDataAccessException. getAllRegistrationFormMappings below
    //    filters the same way.
    FormMapping findBySubjectTypeAndProgramNullAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(SubjectType subjectType, FormType formType, int implVersion);

    default FormMapping getRegistrationFormMapping(SubjectType subjectType) {
        return this.findBySubjectTypeAndProgramNullAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(subjectType, FormType.IndividualProfile, FormMapping.IMPL_VERSION);
    }

    //    Program Enrolment
    FormMapping findBySubjectTypeAndProgramAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(SubjectType subjectType, Program program, FormType formType, int implVersion);

    default FormMapping getProgramEnrolmentFormMapping(SubjectType subjectType, Program program) {
        return findBySubjectTypeAndProgramAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(subjectType, program, FormType.ProgramEnrolment, FormMapping.IMPL_VERSION);
    }

    default FormMapping getProgramExitFormMapping(SubjectType subjectType, Program program) {
        return findBySubjectTypeAndProgramAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(subjectType, program, FormType.ProgramExit, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findAllBySubjectTypeAndProgramNotNullAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(SubjectType subjectType, FormType formType, int implVersion);

    default List<FormMapping> getAllProgramEnrolmentFormMapping(SubjectType subjectType) {
        return findAllBySubjectTypeAndProgramNotNullAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(subjectType, FormType.ProgramEnrolment, FormMapping.IMPL_VERSION);
    }

    default List<FormMapping> getAllProgramEnrolmentFormMapping(List<SubjectType> subjectTypes) {
        return findAllBySubjectTypeInAndProgramNotNullAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(subjectTypes, FormType.ProgramEnrolment, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findAllBySubjectTypeInAndProgramNotNullAndEncounterTypeNullAndFormFormTypeAndImplVersionAndIsVoidedFalse(List<SubjectType> subjectTypes, FormType formType, int implVersion);

    List<FormMapping> findByFormFormTypeAndImplVersionAndIsVoidedFalse(FormType formType, int implVersion);

    default List<FormMapping> findByFormFormTypeAndIsVoidedFalse(FormType formType) {
        return findByFormFormTypeAndImplVersionAndIsVoidedFalse(formType, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findByFormFormTypeAndImplVersionAndIsVoidedTrueOrderByLastModifiedDateTimeDesc(FormType formType, int implVersion);

    default List<FormMapping> findByFormFormTypeAndIsVoidedTrueOrderByLastModifiedDateTimeDesc(FormType formType) {
        return this.findByFormFormTypeAndImplVersionAndIsVoidedTrueOrderByLastModifiedDateTimeDesc(formType, FormMapping.IMPL_VERSION);
    }

    default List<FormMapping> getAllProgramEnrolmentFormMappings() {
        return findByFormFormTypeAndIsVoidedFalse(FormType.ProgramEnrolment);
    }

    //    Program Encounter
    FormMapping findBySubjectTypeAndProgramAndEncounterTypeAndIsVoidedFalseAndFormFormTypeAndImplVersion(SubjectType subjectType, Program program, EncounterType encounterType, FormType formType, int implVersion);

    default FormMapping findBySubjectTypeAndProgramAndEncounterTypeAndIsVoidedFalseAndFormFormType(SubjectType subjectType, Program program, EncounterType encounterType, FormType formType) {
        return this.findBySubjectTypeAndProgramAndEncounterTypeAndIsVoidedFalseAndFormFormTypeAndImplVersion(subjectType, program, encounterType, formType, FormMapping.IMPL_VERSION);
    }

    default FormMapping getProgramEncounterFormMapping(SubjectType subjectType, Program program, EncounterType encounterType) {
        return findBySubjectTypeAndProgramAndEncounterTypeAndIsVoidedFalseAndFormFormType(subjectType, program, encounterType, FormType.ProgramEncounter);
    }

    default FormMapping getProgramEncounterCancelFormMapping(SubjectType subjectType, Program program, EncounterType encounterType) {
        return findBySubjectTypeAndProgramAndEncounterTypeAndIsVoidedFalseAndFormFormType(subjectType, program, encounterType, FormType.ProgramEncounterCancellation);
    }

    List<FormMapping> findAllBySubjectTypeAndProgramAndEncounterTypeNotNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(SubjectType subjectType, Program program, FormType formType, int implVersion);

    default List<FormMapping> getAllProgramEncounterFormMappings(SubjectType subjectType, Program program) {
        return findAllBySubjectTypeAndProgramAndEncounterTypeNotNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(subjectType, program, FormType.ProgramEncounter, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findByEncounterTypeNotNullAndProgramNotNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(FormType programEncounter, int implVersion);

    default List<FormMapping> getAllProgramEncounterFormMappings() {
        return findByEncounterTypeNotNullAndProgramNotNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(FormType.ProgramEncounter, FormMapping.IMPL_VERSION);
    }

    //    General Encounter
    FormMapping findBySubjectTypeAndProgramNullAndEncounterTypeAndIsVoidedFalseAndFormFormTypeAndImplVersion(SubjectType subjectType, EncounterType encounterType, FormType formType, int implVersion);

    default FormMapping getGeneralEncounterFormMapping(SubjectType subjectType, EncounterType encounterType) {
        return findBySubjectTypeAndProgramNullAndEncounterTypeAndIsVoidedFalseAndFormFormTypeAndImplVersion(subjectType, encounterType, FormType.Encounter, FormMapping.IMPL_VERSION);
    }

    default FormMapping getGeneralEncounterCancelFormMapping(SubjectType subjectType, EncounterType encounterType) {
        return findBySubjectTypeAndProgramNullAndEncounterTypeAndIsVoidedFalseAndFormFormTypeAndImplVersion(subjectType, encounterType, FormType.IndividualEncounterCancellation, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findAllBySubjectTypeAndProgramNullAndEncounterTypeNotNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(SubjectType subjectType, FormType formType, int implVersion);

    default List<FormMapping> getAllGeneralEncounterFormMappings(SubjectType subjectType) {
        return findAllBySubjectTypeAndProgramNullAndEncounterTypeNotNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(subjectType, FormType.Encounter, FormMapping.IMPL_VERSION);
    }

    default List<FormMapping> getAllGeneralEncounterFormMappings() {
        return findAllByProgramNullAndEncounterTypeNotNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(FormType.Encounter, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findAllByProgramNullAndEncounterTypeNotNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(FormType formType, int implVersion);

    List<FormMapping> findAllByProgramNullAndEncounterTypeNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(FormType formType, int implVersion);

    default List<FormMapping> getAllRegistrationFormMappings() {
        return findAllByProgramNullAndEncounterTypeNullAndIsVoidedFalseAndFormFormTypeAndImplVersion(FormType.IndividualProfile, FormMapping.IMPL_VERSION);
    }

    @Query("select fm from FormMapping fm " +
            "join fm.form f " +
            "join fm.subjectType " +
            "left join fm.program " +
            "left join fm.encounterType " +
            "where (:encounterTypeUUID is null or fm.encounterType.uuid = :encounterTypeUUID) " +
            "and (:programUUID is null or fm.program.uuid = :programUUID) " +
            "and fm.subjectType.uuid = :subjectTypeUUID " +
            "and f.formType = :formType " +
            "and fm.implVersion = 1 ")
    FormMapping getRequiredFormMapping(String subjectTypeUUID, String programUUID, String encounterTypeUUID, FormType formType);    //left join to fetch eagerly in first select

    @Query("select fm from FormMapping fm " +
            "left join fetch fm.form f " +
            "left join fetch f.formElementGroups fg " +
            "left join fetch fg.formElements fe " +
            "left join fetch fe.concept q " +
            "left join fetch q.conceptAnswers ca " +
            "left join fetch ca.answerConcept a " +
            "left join fetch fm.encounterType et " +
            "left join fetch fm.program p " +
            "left join fetch fm.subjectType s " +
            "where (:encounterTypeUUID is null or fm.encounterType.uuid = :encounterTypeUUID) " +
            "and (:programUUID is null or fm.program.uuid = :programUUID) " +
            "and (:subjectTypeUUID is null or fm.subjectType.uuid = :subjectTypeUUID) " +
            "and (:formType is null or f.formType = :formType) " +
            "and fm.implVersion = 1 " +
            "and fm.isVoided = false ")
    List<FormMapping> findRequiredFormMappings(String subjectTypeUUID, String programUUID, String encounterTypeUUID, FormType formType);

    @Query(value = "select distinct on (subject_type_id, observations_type_entity_id, entity_id) * \n" +
            "from form_mapping \n" +
            "where is_voided = false \n" +
            " and impl_version = 1 \n" +
            "  and entity_id isnull \n" +
            "  and observations_type_entity_id notnull", nativeQuery = true)
    List<FormMapping> findByProgramNullAndEncounterTypeNotNullAndIsVoidedFalse();

    @Query(value = "select distinct on (subject_type_id, observations_type_entity_id, entity_id) * \n" +
            "from form_mapping \n" +
            "where is_voided = false \n" +
            "  and impl_version = 1 \n" +
            "  and entity_id notnull \n" +
            "  and observations_type_entity_id notnull", nativeQuery = true)
    List<FormMapping> findByProgramNotNullAndEncounterTypeNotNullAndIsVoidedFalse();

    /**
     * Every non-voided mapping on exactly this (subject type, programme, visit type) combination whose
     * approval switch is on. Organisation scoping is left to row level security (V1_398), as with every
     * other query here - form_mapping is never filtered on organisation_id in Java.
     *
     * The left joins are load-bearing. With an implicit join (fm.program.id) Hibernate emits an INNER
     * JOIN for the whole query, which drops every mapping that has no programme - that is, all of the
     * subject-type-only and general-encounter shapes. The "is null" branches would not save it, because
     * the join is applied query-wide rather than per-branch.
     */
    @Query("select fm from FormMapping fm " +
            "left join fm.program p " +
            "left join fm.encounterType et " +
            "where fm.subjectType.id = :subjectTypeId " +
            "and ((:programId is null and p.id is null) or p.id = :programId) " +
            "and ((:encounterTypeId is null and et.id is null) or et.id = :encounterTypeId) " +
            "and fm.enableApproval = true " +
            "and fm.isVoided = false " +
            "and fm.implVersion = :implVersion")
    List<FormMapping> findApprovalEnabledMappingsForCombinationAndImplVersion(@Param("subjectTypeId") Long subjectTypeId,
                                                                             @Param("programId") Long programId,
                                                                             @Param("encounterTypeId") Long encounterTypeId,
                                                                             @Param("implVersion") int implVersion);

    default List<FormMapping> findApprovalEnabledMappingsForCombination(Long subjectTypeId, Long programId, Long encounterTypeId) {
        return findApprovalEnabledMappingsForCombinationAndImplVersion(subjectTypeId, programId, encounterTypeId, FormMapping.IMPL_VERSION);
    }

    /**
     * The Approval or Rejection form attached to exactly this (subject type, programme, visit type)
     * combination, or null if none is. Used to validate the answers on an approval decision against the
     * form they were captured on (EntityApprovalStatusService#save).
     *
     * Both may be attached to one combination, so the form type is part of the lookup rather than
     * something to filter afterwards. The left joins carry over from
     * findApprovalEnabledMappingsForCombination above, for the same reason: an implicit join makes the
     * whole query an INNER JOIN and drops every combination that has no programme.
     *
     * check_form_mapping_uniqueness makes at most one non-voided mapping possible per combination and
     * form type, so a single result is safe to return.
     */
    @Query("select fm from FormMapping fm " +
            "left join fm.program p " +
            "left join fm.encounterType et " +
            "where fm.subjectType.id = :subjectTypeId " +
            "and ((:programId is null and p.id is null) or p.id = :programId) " +
            "and ((:encounterTypeId is null and et.id is null) or et.id = :encounterTypeId) " +
            "and fm.form.formType = :formType " +
            "and fm.isVoided = false " +
            "and fm.implVersion = :implVersion")
    FormMapping findDecisionFormMappingForCombinationAndImplVersion(@Param("subjectTypeId") Long subjectTypeId,
                                                                    @Param("programId") Long programId,
                                                                    @Param("encounterTypeId") Long encounterTypeId,
                                                                    @Param("formType") FormType formType,
                                                                    @Param("implVersion") int implVersion);

    default FormMapping findDecisionFormMappingForCombination(Long subjectTypeId, Long programId, Long encounterTypeId, FormType formType) {
        return findDecisionFormMappingForCombinationAndImplVersion(subjectTypeId, programId, encounterTypeId, formType, FormMapping.IMPL_VERSION);
    }

    @Query("select st from SubjectType st " +
            "left join FormMapping fm on st.id = fm.subjectType.id " +
            "where fm.form.uuid = :formUUID ")
    List<SubjectType> getSubjectTypesMappedToAForm(@Param("formUUID") String formUUID);

    @Query("SELECT p.name FROM Program p " +
            "WHERE p.id IN (SELECT fm.program.id FROM FormMapping fm WHERE fm.form.uuid = :formUUID)")
    List<String> getProgramsMappedToAForm(@Param("formUUID") String formUUID);

    @Override
    default <S extends FormMapping> List<S> saveAll(Iterable<S> entities) {
        throw new RuntimeException("Not supported");
    }

    @Override
    default <S extends FormMapping> S saveAndFlush(S entity) {
        throw new RuntimeException("Not supported");
    }

    default FormMapping saveFormMapping(FormMapping formMapping) {
        formMapping.ensureVersion();
        return this.save(formMapping);
    }

    List<FormMapping> findAllByOrganisationIdAndImplVersion(Long organisationId, int implVersion);
    @Override
    default List<FormMapping> findAllByOrganisationId(Long organisationId) {
        return this.findAllByOrganisationIdAndImplVersion(organisationId, FormMapping.IMPL_VERSION);
    }

    default List<FormMapping> getAllProgramEncounterTypeFormMapping(List<SubjectType> subjectTypes, List<Program> programs) {
        return this.findAllBySubjectTypeInAndProgramInAndEncounterTypeNotNullAndIsVoidedFalseAndImplVersion(subjectTypes, programs, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findAllBySubjectTypeInAndProgramInAndEncounterTypeNotNullAndIsVoidedFalseAndImplVersion(List<SubjectType> subjectTypes, List<Program> programs, int implVersion);

    default List<FormMapping> getAllGeneralEncounterTypeFormMapping(List<SubjectType> subjectTypes) {
        return this.findAllBySubjectTypeInAndProgramNullAndEncounterTypeNotNullAndIsVoidedFalseAndImplVersion(subjectTypes, FormMapping.IMPL_VERSION);
    }

    List<FormMapping> findAllBySubjectTypeInAndProgramNullAndEncounterTypeNotNullAndIsVoidedFalseAndImplVersion(List<SubjectType> subjectTypes, int implVersion);
}
