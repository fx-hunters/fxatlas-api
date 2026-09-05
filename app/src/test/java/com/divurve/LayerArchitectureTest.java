package com.divurve;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.common.architecture.PersistenceAdapter;
import com.divurve.common.architecture.UseCase;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.engine.EngineComponent;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 클래스 레이어 규칙 — Controller/Service 경계 (문서 4.2).
 * 어노테이션으로 정의한 레이어의 호출 방향을 강제한다.
 *
 * <p>뼈대 단계에서는 아직 레이어 어노테이션을 붙인 클래스가 없으므로
 * {@code withOptionalLayers(true)} 로 빈 레이어를 허용한다. 실제 클래스가 생기면 규칙이 그대로 적용된다.
 */
@AnalyzeClasses(packages = "com.divurve", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule 레이어_의존_방향 = layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .withOptionalLayers(true)
        .layer("Web").definedBy(annotatedWith(WebAdapter.class))
        .layer("UseCase").definedBy(annotatedWith(UseCase.class))
        .layer("Persistence").definedBy(annotatedWith(PersistenceAdapter.class))
        .layer("External").definedBy(annotatedWith(ExternalAdapter.class))
        .layer("Engine").definedBy(annotatedWith(EngineComponent.class))
        .whereLayer("Web").mayNotBeAccessedByAnyLayer()
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("UseCase")
        .whereLayer("External").mayOnlyBeAccessedByLayers("UseCase")
        .whereLayer("Engine").mayOnlyBeAccessedByLayers("UseCase");

    @ArchTest
    static final ArchRule engine은_프레임워크에_의존하지_않는다 = noClasses()
        .that().resideInAPackage("..engine..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..");
}
