package org.avni.server.exporter;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.exporter.v2.ExportV2CSVFieldExtractor;
import org.avni.server.exporter.v2.ExportV2Processor;
import org.avni.server.exporter.v2.LongitudinalExportV2TaskletImpl;
import org.avni.server.framework.security.AuthService;
import org.avni.server.service.ExportS3Service;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.web.external.request.export.ExportFilters;
import org.avni.server.web.external.request.export.ExportOutput;
import org.avni.server.web.external.request.export.ReportType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

@Configuration
//@EnableBatchProcessing
public class ExportBatchConfiguration {
    private final int CHUNK_SIZE = 100;
    private final EntityManager entityManager;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final IndividualRepository individualRepository;
    private final GroupSubjectRepository groupSubjectRepository;
    private final AuthService authService;
    private final ExportS3Service exportS3Service;
    private final LocationRepository locationRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ProgramRepository programRepository;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final int longitudinalExportV2Limit;
    private final int legacyLongitudinalExportLimit;

    @Autowired
    public ExportBatchConfiguration(ProgramEnrolmentRepository programEnrolmentRepository,
                                    IndividualRepository individualRepository,
                                    GroupSubjectRepository groupSubjectRepository,
                                    AuthService authService,
                                    ExportS3Service exportS3Service,
                                    LocationRepository locationRepository,
                                    SubjectTypeRepository subjectTypeRepository,
                                    EncounterTypeRepository encounterTypeRepository,
                                    ProgramRepository programRepository,
                                    EntityManager entityManager,
                                    JobRepository jobRepository,
                                    PlatformTransactionManager platformTransactionManager,
                                    @Value("${avni.longitudinal.export.v2.limit}") int longitudinalExportV2Limit,
                                    @Value("${avni.legacy.longitudinal.export.limit}") int legacyLongitudinalExportLimit
    ) {
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.individualRepository = individualRepository;
        this.groupSubjectRepository = groupSubjectRepository;
        this.authService = authService;
        this.exportS3Service = exportS3Service;
        this.locationRepository = locationRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.programRepository = programRepository;
        this.entityManager = entityManager;
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.longitudinalExportV2Limit = longitudinalExportV2Limit;
        this.legacyLongitudinalExportLimit = legacyLongitudinalExportLimit;
    }

    @Bean
    public Job exportVisitJob(JobCompletionNotificationListener listener, Step step1) {
        return new JobBuilder("exportVisitJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(step1)
                .build();
    }

    @Bean
    public Job exportV2Job(JobCompletionNotificationListener listener, Step exportV2Step) {
        return new JobBuilder("exportV2Job", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(exportV2Step)
                .build();
    }

    @Bean
    public Step exportV2Step(Tasklet exportV2Tasklet,
                             LongitudinalExportJobStepListener listener) {
        return new StepBuilder("exportV2Step", jobRepository)
                .tasklet(exportV2Tasklet, platformTransactionManager)
                .listener(listener)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportV2Tasklet(@Value("#{jobParameters['uuid']}") String uuid,
                                   @Value("#{jobParameters['userId']}") Long userId,
                                   @Value("#{jobParameters['organisationUUID']}") String organisationUUID,
                                   @Value("#{jobParameters['exportJobParamsUUID']}") String exportJobParamsUUID,
                                   LongitudinalExportJobStepListener listener,
                                   ExportV2CSVFieldExtractor exportV2CSVFieldExtractor,
                                   ExportV2Processor exportV2Processor) {
        authService.authenticateByUserId(userId, organisationUUID);
        ExportOutput exportOutput = exportV2CSVFieldExtractor.getExportOutput();
        ExportFilters subjectFilters = exportOutput.getFilters();
        List<Long> addressLevelIds = subjectFilters.getAddressLevelIds();
        List<Long> selectedAddressIds = getLocations(addressLevelIds);
        List<Long> addressParam = selectedAddressIds.isEmpty() ? null : selectedAddressIds;
        Stream stream = getRegistrationStream(exportOutput.getUuid(), addressParam, subjectFilters.getDate().getFrom().toLocalDate(), subjectFilters.getDate().getTo().toLocalDate(), subjectFilters.includeVoided());
        Stream alteredStream = truncateStream(stream);
        LongitudinalExportTasklet encounterTasklet = new LongitudinalExportV2TaskletImpl(CHUNK_SIZE, entityManager, exportV2CSVFieldExtractor, exportV2Processor, exportS3Service, uuid, alteredStream);
        listener.setItemReaderCleaner(encounterTasklet);
        return encounterTasklet;
    }

    private Stream truncateStream(Stream stream) {
        return stream.limit(longitudinalExportV2Limit); //Truncate stream
    }

    @Bean
    public Step step1(Tasklet tasklet,
                      LongitudinalExportJobStepListener listener) {
        return new StepBuilder("step1", jobRepository)
                .tasklet(tasklet, platformTransactionManager)
                .listener(listener)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet tasklet(@Value("#{jobParameters['uuid']}") String uuid,
                           @Value("#{jobParameters['userId']}") Long userId,
                           @Value("#{jobParameters['organisationUUID']}") String organisationUUID,
                           @Value("#{jobParameters['programUUID']}") String programUUID,
                           @Value("#{jobParameters['subjectTypeUUID']}") String subjectTypeUUID,
                           @Value("#{jobParameters['encounterTypeUUID']}") String encounterTypeUUID,
                           @Value("#{jobParameters['startDate']}") Date startDate,
                           @Value("#{jobParameters['endDate']}") Date endDate,
                           @Value("#{jobParameters['reportType']}") String reportType,
                           @Value("#{jobParameters['addressIds']}") String addressIds,
                           @Value("#{jobParameters['includeVoided']}") String includeVoided,
                           @Value("#{jobParameters['timeZone']}") String timeZone,
                           LongitudinalExportJobStepListener listener,
                           ExportCSVFieldExtractor exportCSVFieldExtractor,
                           ExportProcessor exportProcessor) {
        authService.authenticateByUserId(userId, organisationUUID);
        final Map<String, Sort.Direction> sorts = new HashMap<>();
        sorts.put("id", Sort.Direction.ASC);
        List<Long> locationIds = addressIds.isEmpty() ? Collections.emptyList() : Arrays.stream(addressIds.split(",")).map(Long::valueOf).collect(Collectors.toList());
        List<Long> selectedAddressIds = getLocations(locationIds);
        List<Long> addressParam = selectedAddressIds.isEmpty() ? null : selectedAddressIds;
        boolean isVoidedIncluded = Boolean.parseBoolean(includeVoided);
        DateTimeZone dateTimeZone = DateTimeZone.forID(timeZone);
        LocalDate startDateForZone = new LocalDate(startDate, dateTimeZone);
        LocalDate endDateForZone = new LocalDate(endDate, dateTimeZone);
        DateTime startDateTimeWithZone = new DateTime(startDate).withZone(dateTimeZone);
        DateTime endDateTimeWithZone = new DateTime(endDate).withZone(dateTimeZone);
        Stream stream;
        switch (ReportType.valueOf(reportType)) {
            case Registration:
                stream = getRegistrationStream(subjectTypeUUID, addressParam, startDateForZone, endDateForZone, isVoidedIncluded);
                break;
            case Enrolment:
                stream = getEnrolmentStream(programUUID, addressParam, startDateTimeWithZone, endDateTimeWithZone, isVoidedIncluded);
                break;
            case Encounter:
                stream = getEncounterStream(programUUID, encounterTypeUUID, addressParam, startDateTimeWithZone, endDateTimeWithZone, isVoidedIncluded);
                break;
            case GroupSubject:
                stream = getGroupSubjectStream(subjectTypeUUID, addressParam, startDateForZone, endDateForZone, sorts, isVoidedIncluded);
                break;
            default:
                throw new RuntimeException(format("Unknown report type: '%s'", reportType));
        }

        Stream alteredStream = stream.limit(this.legacyLongitudinalExportLimit);
        LongitudinalExportTasklet encounterTasklet = new LongitudinalExportTaskletImpl(CHUNK_SIZE, entityManager, exportCSVFieldExtractor, exportProcessor, exportS3Service, uuid, alteredStream);
        listener.setItemReaderCleaner(encounterTasklet);
        return encounterTasklet;
    }

    private Stream getGroupSubjectStream(String subjectTypeUUID, List<Long> addressParam, LocalDate startDate, LocalDate endDate, Map<String, Sort.Direction> sorts, boolean isVoidedIncluded) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        return isVoidedIncluded ? groupSubjectRepository.findAllGroupSubjects(subjectType.getId(), addressParam, DateTimeUtil.toInstant(startDate), DateTimeUtil.toInstant(endDate)) :
                groupSubjectRepository.findNonVoidedGroupSubjects(subjectType.getId(), addressParam, DateTimeUtil.toInstant(startDate), DateTimeUtil.toInstant(endDate));
    }

    private Stream getEncounterStream(String programUUID, String encounterTypeUUID, List<Long> addressParam, DateTime startDateTime, DateTime endDateTime, boolean isVoidedIncluded) {
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUUID);
        if (programUUID == null) {
            return isVoidedIncluded ? individualRepository.findAllEncounters(addressParam, DateTimeUtil.toInstant(startDateTime), DateTimeUtil.toInstant(endDateTime), encounterType.getId()) :
                    individualRepository.findNonVoidedEncounters(addressParam, DateTimeUtil.toInstant(startDateTime), DateTimeUtil.toInstant(endDateTime), encounterType.getId());
        } else {
            Program program = programRepository.findByUuid(programUUID);
            return isVoidedIncluded ? programEnrolmentRepository.findAllProgramEncounters(addressParam, DateTimeUtil.toInstant(startDateTime), DateTimeUtil.toInstant(endDateTime), encounterType.getId(), program.getId()) :
                    programEnrolmentRepository.findNonVoidedProgramEncounters(addressParam, DateTimeUtil.toInstant(startDateTime), DateTimeUtil.toInstant(endDateTime), encounterType.getId(), program.getId());
        }
    }

    private Stream getEnrolmentStream(String programUUID, List<Long> addressParam, DateTime startDateTime, DateTime endDateTime, boolean isVoidedIncluded) {
        Program program = programRepository.findByUuid(programUUID);
        return isVoidedIncluded ? programEnrolmentRepository.findAllEnrolments(program.getId(), addressParam, DateTimeUtil.toInstant(startDateTime), DateTimeUtil.toInstant(endDateTime)) :
                programEnrolmentRepository.findNonVoidedEnrolments(program.getId(), addressParam, DateTimeUtil.toInstant(startDateTime), DateTimeUtil.toInstant(endDateTime));
    }

    private Stream getRegistrationStream(String subjectTypeUUID, List<Long> addressParam, LocalDate startDateTime, LocalDate endDateTime, boolean includeVoided) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        return includeVoided ? individualRepository.findAllIndividuals(subjectType.getId(), addressParam, DateTimeUtil.toInstant(startDateTime), DateTimeUtil.toInstant(endDateTime)) :
                individualRepository.findNonVoidedIndividuals(subjectType.getId(), addressParam, DateTimeUtil.toInstant(startDateTime), DateTimeUtil.toInstant(endDateTime));
    }

    private List<Long> getLocations(List<Long> locationIds) {
        List<AddressLevel> selectedAddressLevels = locationRepository.findAllById(locationIds);
        List<AddressLevel> allAddressLevels = locationRepository.findAllByIsVoidedFalse();
        return selectedAddressLevels
                .stream()
                .flatMap(al -> findLowestAddresses(al, allAddressLevels))
                .map(CHSBaseEntity::getId)
                .collect(Collectors.toList());
    }

    private Stream<AddressLevel> findLowestAddresses(AddressLevel selectedAddress, List<AddressLevel> allAddresses) {
        return allAddresses
                .stream()
                .filter(al -> al.getLineage().startsWith(selectedAddress.getLineage()));
    }

}
