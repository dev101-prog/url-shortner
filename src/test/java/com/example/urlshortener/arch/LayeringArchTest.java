package com.example.urlshortener.arch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.example.urlshortener.config.ClockConfig;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.time.Instant;
import java.time.LocalDate;

/** URL-NFR-5.1 / design §8.4: the five layering rules, on production classes only. */
@AnalyzeClasses(
    packages = "com.example.urlshortener",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringArchTest {

  /** Rule 1: services have no web, servlet, JDBC, api or infra dependencies. */
  @ArchTest
  void rule1_serviceIsFrameworkFree(JavaClasses classes) {
    noClasses()
        .that()
        .resideInAPackage("..service..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..api..",
            "..infra..",
            "jakarta.servlet..",
            "org.springframework.web..",
            "org.springframework.jdbc..")
        .check(classes);
  }

  /** Rule 2a: repositories never depend on the api layer. */
  @ArchTest
  void rule2_repositoryDoesNotDependOnApi(JavaClasses classes) {
    noClasses()
        .that()
        .resideInAPackage("..repository..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..api..")
        .check(classes);
  }

  /** Rule 2b: repositories may use service.domain, but nothing else from service. */
  @ArchTest
  void rule2_repositoryOnlyUsesServiceDomain(JavaClasses classes) {
    noClasses()
        .that()
        .resideInAPackage("..repository..")
        .should()
        .dependOnClassesThat(
            resideInAPackage("..service..").and(not(resideInAPackage("..service.domain.."))))
        .check(classes);
  }

  /** Rule 3: controllers call services only, never repositories. */
  @ArchTest
  void rule3_apiDoesNotDependOnRepository(JavaClasses classes) {
    noClasses()
        .that()
        .resideInAPackage("..api..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..repository..")
        .check(classes);
  }

  /**
   * Rule 4: infra depends only on service.port, service.domain and repository (plus the JDK and the
   * libraries it adapts: Caffeine, Micrometer, Spring context/scheduling, SLF4J).
   */
  @ArchTest
  void rule4_infraOnlyUsesPortsDomainAndRepository(JavaClasses classes) {
    classes()
        .that()
        .resideInAPackage("..infra..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "com.example.urlshortener.infra..",
            "com.example.urlshortener.service.port..",
            "com.example.urlshortener.service.domain..",
            "com.example.urlshortener.repository..",
            "java..",
            "com.github.benmanes.caffeine..",
            "io.micrometer..",
            "org.springframework.stereotype..",
            "org.springframework.context..",
            "org.springframework.scheduling..",
            "org.slf4j..",
            "")
        .check(classes);
  }

  /** Rule 5: time only comes from the injected Clock (design D9). */
  @ArchTest
  void rule5_noWallClockOutsideClockConfig(JavaClasses classes) {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(ClockConfig.class.getName())
        .should()
        .callMethod(Instant.class, "now")
        .orShould()
        .callMethod(LocalDate.class, "now")
        .orShould()
        .callMethod(System.class, "currentTimeMillis")
        .check(classes);
  }
}
