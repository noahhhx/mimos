package io.github.noahhhx.mimos.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.github.noahhhx.mimos.api.support.RequestLoggingConfig;
import io.github.noahhhx.mimos.api.support.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Repository;

/**
 * The module seams and domain-code rules from AGENTS.md, checked against
 * the main classes of every backend module.
 */
@AnalyzeClasses(packages = "io.github.noahhhx.mimos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "io.github.noahhhx.mimos.";
    private static final String[] CORE = {BASE + "recipes..", BASE + "planning..", BASE + "fueling.."};
    private static final String[] API_AND_INTEGRATIONS = {
        BASE + "api..", BASE + "plugins..", BASE + "intervalsicu..", BASE + "openfoodfacts.."
    };
    private static final Set<String> WIRING = Set.of(TimeConfig.class.getName(), RequestLoggingConfig.class.getName());
    private static final Set<String> TEMPORALS = Set.of(
            Instant.class.getName(),
            LocalDate.class.getName(),
            LocalDateTime.class.getName(),
            LocalTime.class.getName(),
            OffsetDateTime.class.getName(),
            ZonedDateTime.class.getName());

    @ArchTest
    static final ArchRule clocksAndRandomnessAreInjected = noClasses()
            .that(DescribedPredicate.describe("are not wiring classes", (JavaClass c) -> !WIRING.contains(c.getName())))
            .should()
            .callCodeUnitWhere(DescribedPredicate.describe(
                    "read the system clock or make their own randomness", ArchitectureTest::readsTheSystem))
            .because("domain code injects TimeConfig's Clock, SecureRandom, and IdGenerator, so tests can pin them");

    @ArchTest
    static final ArchRule repositoriesStayInTheirModule = classes()
            .should(new ArchCondition<JavaClass>("use only their own module's repositories") {
                @Override
                public void check(JavaClass origin, ConditionEvents events) {
                    for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        if (target.getPackageName().startsWith(BASE)
                                && isRepository(target)
                                && !module(target).equals(module(origin))) {
                            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                        }
                    }
                }
            })
            .because("modules talk through each other's public services, never their tables");

    @ArchTest
    static final ArchRule coreDependsOnNoAppOrIntegration = noClasses()
            .that()
            .resideInAnyPackage(CORE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(API_AND_INTEGRATIONS)
            .because("core modules are pure domain; apps/api and integrations depend on them, never the reverse");

    @ArchTest
    static final ArchRule coreKnowsNothingAboutHouseholds = noClasses()
            .that()
            .resideInAnyPackage(CORE)
            .should()
            .haveSimpleNameContaining("Household")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleNameContaining("Household")
            .because("core takes an opaque ownerId; apps/api decides it is the caller's household (ADR-0019)");

    private static boolean readsTheSystem(JavaCall<?> call) {
        String owner = call.getTargetOwner().getName();
        String name = call.getName();
        if (TEMPORALS.contains(owner) && name.equals("now")) {
            return call.getTarget().getRawParameterTypes().stream().noneMatch(type -> type.isEquivalentTo(Clock.class));
        }
        return (owner.equals(Clock.class.getName()) && name.startsWith("system"))
                || (owner.equals(UUID.class.getName()) && name.equals("randomUUID"))
                || (owner.equals(Math.class.getName()) && name.equals("random"))
                || (owner.equals(ThreadLocalRandom.class.getName()) && name.equals("current"))
                || (call.getTargetOwner().isAssignableTo(Random.class) && name.equals("<init>"));
    }

    private static boolean isRepository(JavaClass type) {
        return type.isAnnotatedWith(Repository.class)
                || type.getSimpleName().endsWith("Repository")
                || type.getEnclosingClass().map(ArchitectureTest::isRepository).orElse(false);
    }

    /** The first package segment under the base package: recipes, planning, api, plugins, … */
    private static String module(JavaClass type) {
        String rest = type.getPackageName().substring(BASE.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
