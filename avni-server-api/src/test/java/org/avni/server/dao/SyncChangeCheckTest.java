package org.avni.server.dao;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.dao.program.SubjectProgramEligibilityRepository;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.sync.SyncEntityName;
import org.joda.time.DateTime;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SyncChangeCheckTest {
    private static final List<Class<? extends OperatingIndividualScopeAwareRepository>> SPECIFICATION_REPOSITORIES = Arrays.asList(
            ChecklistItemRepository.class,
            ChecklistRepository.class,
            CommentRepository.class,
            CommentThreadRepository.class,
            EncounterRepository.class,
            EntityApprovalStatusRepository.class,
            GroupSubjectRepository.class,
            IndividualRelationshipRepository.class,
            IndividualRepository.class,
            SubjectProgramEligibilityRepository.class,
            ProgramEncounterRepository.class,
            SubjectMigrationRepository.class,
            ProgramEnrolmentRepository.class);

    private static final List<Class<?>> NATIVE_QUERY_REPOSITORIES = Arrays.asList(
            LocationRepository.class,
            LocationMappingRepository.class,
            AttendanceRecordRepository.class,
            SessionRepository.class,
            CalendarRepository.class,
            CalendarDateMarkerRepository.class);

    @Test
    public void specificationChangeChecksStopAtTheFirstRowInsteadOfCounting() {
        for (Class<? extends OperatingIndividualScopeAwareRepository> repositoryClass : SPECIFICATION_REPOSITORIES) {
            OperatingIndividualScopeAwareRepository repository = mock(repositoryClass, CALLS_REAL_METHODS);

            repository.isEntityChanged(syncParameters());

            verify(repository, never().description(repositoryClass.getSimpleName() + " counts rows")).count(any(Specification.class));
            verify(repository, description(repositoryClass.getSimpleName() + " does not use existsIgnoringOrder")).existsIgnoringOrder(any(Specification.class));
        }
    }

    @Test
    public void existsIgnoringOrderClearsTheOrderingTheSpecificationAdds() {
        IndividualRepository repository = mock(IndividualRepository.class, CALLS_REAL_METHODS);
        Root<Individual> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Order order = mock(Order.class);
        Specification<Individual> orderingSpecification = (r, q, b) -> {
            q.orderBy(order);
            return null;
        };

        repository.existsIgnoringOrder(orderingSpecification);
        ArgumentCaptor<Specification> specificationPassedToExists = ArgumentCaptor.forClass(Specification.class);
        verify(repository).exists(specificationPassedToExists.capture());
        specificationPassedToExists.getValue().toPredicate(root, query, cb);

        InOrder inOrder = inOrder(query);
        inOrder.verify(query).orderBy(order);
        inOrder.verify(query).orderBy(Collections.emptyList());
    }

    @Test
    public void nativeChangeChecksAskWhetherARowExistsInsteadOfCounting() {
        for (Class<?> repositoryClass : NATIVE_QUERY_REPOSITORIES) {
            List<Method> changeChecks = Arrays.stream(repositoryClass.getDeclaredMethods())
                    .filter(method -> method.getName().startsWith("hasChanged"))
                    .toList();
            assertEquals(repositoryClass.getSimpleName(), 1, changeChecks.size());
            Method changeCheck = changeChecks.get(0);
            assertEquals(repositoryClass.getSimpleName(), boolean.class, changeCheck.getReturnType());
            String sql = changeCheck.getAnnotation(Query.class).value().toLowerCase();
            assertTrue(repositoryClass.getSimpleName() + ": " + sql, sql.startsWith("select exists(select 1"));
            assertFalse(repositoryClass.getSimpleName() + ": " + sql, sql.contains("count("));
        }
    }

    private SyncParameters syncParameters() {
        Catchment catchment = new Catchment();
        catchment.setId(1L);
        return new SyncParameters(DateTime.now().minusDays(1), DateTime.now(), 1L, "entityTypeUuid", null,
                Collections.emptyList(), new SubjectType(), null, SyncEntityName.Individual, catchment);
    }
}
