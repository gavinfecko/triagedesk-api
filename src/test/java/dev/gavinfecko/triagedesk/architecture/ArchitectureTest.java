package dev.gavinfecko.triagedesk.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * The module rules from docs/ARCHITECTURE.md §3, enforced on every build. Production classes only.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "dev.gavinfecko.triagedesk";

    /** Feature modules only talk through their public API; no package cycles between them. */
    @ArchTest
    static final ArchRule modulesAreFreeOfCycles =
            slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

    /** A module's {@code internal} package is off limits to every other module. */
    @ArchTest
    static final ArchRule internalPackagesStayInternal = slices().matching(ROOT + ".(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(DescribedPredicate.alwaysTrue(), resideOutsideOfPackage("..internal.."))
            .as("modules must not depend on another module's internal package");

    /** Controllers call application services, never repositories or infrastructure directly. */
    @ArchTest
    static final ArchRule controllersDoNotTouchInfrastructure = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infra..")
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(org.springframework.data.repository.Repository.class);

    /** Repositories are used only inside their own module (application or infra layers). */
    @ArchTest
    static final ArchRule repositoriesAreModulePrivate = classes()
            .that()
            .areAssignableTo(org.springframework.data.repository.Repository.class)
            .should()
            .onlyHaveDependentClassesThat()
            .resideInAnyPackage("..application..", "..infra..", "..domain..");

    /** Constructor injection only. */
    @ArchTest
    static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    /** Time comes from the injected {@link Clock}; only common.time may read the wall clock. */
    @ArchTest
    static final ArchRule timeComesFromTheClock = noClasses()
            .that()
            .resideOutsideOfPackage("..common.time..")
            .should()
            .callMethod(Instant.class, "now")
            .orShould()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(LocalDate.class, "now")
            .orShould()
            .callMethod(ZonedDateTime.class, "now")
            .orShould()
            .callMethod(OffsetDateTime.class, "now")
            .orShould()
            .callMethod(Clock.class, "systemUTC")
            .orShould()
            .callMethod(Clock.class, "systemDefaultZone")
            .orShould()
            .callMethod(System.class, "currentTimeMillis")
            .as("read time through the injected java.time.Clock, not the wall clock");
}
