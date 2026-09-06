package com.divurve;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 패키지 단위 규칙 — domain·infra·api 경계 강제 (문서 4.3).
 * "domain 은 infra 를 몰라야 한다" 등 패키지 의존 방향을 실제로 강제한다.
 *
 * <p>뼈대 단계에서는 일부 패키지가 아직 비어 있으므로 {@code withOptionalLayers(true)} 로 허용한다.
 */
@AnalyzeClasses(packages = "com.divurve", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule 패키지_의존_방향 = layeredArchitecture()
        .consideringAllDependencies()
        .withOptionalLayers(true)
        .layer("Common").definedBy("..common..")
        .layer("Engine").definedBy("..engine..")
        .layer("Domain").definedBy("..domain..")
        .layer("Infra").definedBy("..infra..")
        .layer("Api").definedBy("..api..")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Infra", "Api")
        .whereLayer("Engine").mayOnlyBeAccessedByLayers("Domain")
        .whereLayer("Common").mayOnlyBeAccessedByLayers("Engine", "Domain", "Infra", "Api")
        // DIP(문서 3장): domain 은 자신을 움직이는 구현이 infra 에 있다는 것을 모른다.
        // api 도 domain 을 건너뛰고 infra 를 직접 호출하지 않는다. 외부 연동은 전부
        // domain/port 인터페이스를 거치고, 구현체 주입은 Spring 이 런타임에 한다.
        .whereLayer("Infra").mayNotBeAccessedByAnyLayer();
}
