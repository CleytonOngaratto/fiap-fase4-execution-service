package br.com.fiap.postech.carworkshop.execution.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture enforcement: trava as regras da Clean Architecture, no molde do OS Service.
 *
 * <p>All rules are <strong>strict</strong> (no {@code freeze}): this service is born clean, so any
 * violation breaks the build. Plain JUnit 5 {@code @Test} methods (not the ArchUnit JUnit engine) so
 * Surefire counts them. Only production classes are analysed ({@link ImportOption.DoNotIncludeTests}).</p>
 */
class ArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.com.fiap.postech.carworkshop.execution");
    }

    /** Domain stays framework-free: no {@code jakarta.*}, {@code io.quarkus.*} or {@code org.hibernate.*}. */
    @Test
    void domain_is_framework_free() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta..", "io.quarkus..", "org.hibernate..")
                .as("domain must not depend on jakarta.*, io.quarkus.* or org.hibernate.*")
                .check(productionClasses);
    }

    @Test
    void usecase_does_not_depend_on_infrastructure() {
        noClasses()
                .that().resideInAPackage("..usecase..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .as("usecase must not depend on infrastructure..")
                .check(productionClasses);
    }

    /** Strict here (frozen in the OS Service, which inherited older code). */
    @Test
    void usecase_does_not_depend_on_adapter() {
        noClasses()
                .that().resideInAPackage("..usecase..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .as("usecase must not depend on adapter..")
                .check(productionClasses);
    }

    /**
     * Domain and use cases know nothing of AWS (SQS, SNS, DynamoDB), Mercado Pago or New Relic: all of
     * them stay behind ports/adapters.
     */
    @Test
    void domain_and_usecase_do_not_know_external_providers() {
        noClasses()
                .that().resideInAnyPackage("..domain..", "..usecase..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "software.amazon..", "com.amazonaws..", "com.mercadopago..", "com.newrelic..")
                .as("domain and usecase must not depend on AWS, Mercado Pago or New Relic")
                .check(productionClasses);
    }
}
