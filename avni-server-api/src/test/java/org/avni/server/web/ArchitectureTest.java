package org.avni.server.web;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.runner.RunWith;
import com.tngtech.archunit.junit.ArchUnitRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.avni.server", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    private static final Map<String, Set<String>> GET_WRITE_EXCLUSIONS = Map.of(
            "org.avni.server.web.IdentifierAssignmentController", Set.of(),
            "org.avni.server.web.FormController", Set.of("getFormIdentifiers")
    );

    @ArchTest
    public static final ArchRule no_classes_should_use_jakarta_transactional =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .belongToAnyOf(jakarta.transaction.Transactional.class)
                    .because("Spring's @Transactional must be used instead of Jakarta's");

    @ArchTest
    public static final ArchRule get_endpoints_should_have_transactional_read_only =
            methods()
                    .that(areGetEndpoints())
                    .and(areNotExcluded())
                    .should(haveTransactionalReadOnly())
                    .because("GET endpoints must use @Transactional(readOnly = true)");

    private static DescribedPredicate<JavaMethod> areGetEndpoints() {
        return new DescribedPredicate<>("are GET endpoints") {
            @Override
            public boolean test(JavaMethod method) {
                if (method.isAnnotatedWith(GetMapping.class)) {
                    return true;
                }
                if (method.isAnnotatedWith(RequestMapping.class)) {
                    RequestMapping rm = method.getAnnotationOfType(RequestMapping.class);
                    for (RequestMethod m : rm.method()) {
                        if (m == RequestMethod.GET) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    private static DescribedPredicate<JavaMethod> areNotExcluded() {
        return new DescribedPredicate<>("are not excluded from readOnly check") {
            @Override
            public boolean test(JavaMethod method) {
                String className = method.getOwner().getName();
                Set<String> excludedMethods = GET_WRITE_EXCLUSIONS.get(className);
                if (excludedMethods == null) {
                    return true;
                }
                if (excludedMethods.isEmpty()) {
                    return false;
                }
                return !excludedMethods.contains(method.getName());
            }
        };
    }

    private static ArchCondition<JavaMethod> haveTransactionalReadOnly() {
        return new ArchCondition<>("have @Transactional(readOnly = true)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(Transactional.class)) {
                    Transactional txn = method.getAnnotationOfType(Transactional.class);
                    if (!txn.readOnly()) {
                        events.add(SimpleConditionEvent.violated(method,
                                String.format("%s has @Transactional but readOnly is not true", method.getFullName())));
                    }
                    return;
                }

                JavaClass owner = method.getOwner();
                if (owner.isAnnotatedWith(Transactional.class)) {
                    Transactional txn = owner.getAnnotationOfType(Transactional.class);
                    if (!txn.readOnly()) {
                        events.add(SimpleConditionEvent.violated(method,
                                String.format("%s has no method-level @Transactional and class-level @Transactional on %s is not readOnly",
                                        method.getFullName(), owner.getSimpleName())));
                    }
                    return;
                }

                events.add(SimpleConditionEvent.violated(method,
                        String.format("%s has no @Transactional(readOnly = true) at method or class level", method.getFullName())));
            }
        };
    }
}
