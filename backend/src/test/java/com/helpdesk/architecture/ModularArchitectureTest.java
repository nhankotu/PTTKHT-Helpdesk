package com.helpdesk.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModularArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.helpdesk");
    }

    @Test
    @DisplayName("Controller không được gọi trực tiếp Repository (phải đi qua Service)")
    void controllersShouldNotAccessRepositoriesDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..");
        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Các module nghiệp vụ không được truy cập trực tiếp Repository của module khác")
    void modulesShouldNotAccessOtherModuleRepositories() {
        String[] modules = {"identity", "intake", "ticket", "routing", "sla", "knowledge", "reporting"};
        for (String target : modules) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.helpdesk.modules..")
                    .and().resideOutsideOfPackage("com.helpdesk.modules." + target + "..")
                    .should().dependOnClassesThat().resideInAPackage("com.helpdesk.modules." + target + ".repository..")
                    .allowEmptyShould(true);
            rule.check(importedClasses);
        }
    }
}
