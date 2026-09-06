package com.divurve;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.common.architecture.PersistenceAdapter;
import com.divurve.common.architecture.UseCase;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.domain.config.EngineConfig;
import com.divurve.engine.EngineComponent;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

/**
 * 클래스 레이어 규칙 — Controller/Service 경계 (문서 4.2).
 * 어노테이션으로 정의한 레이어의 호출 방향을 강제한다.
 *
 * <p>{@code withOptionalLayers(true)} 는 <b>{@code @PersistenceAdapter} 부착 클래스가 0개</b>이기 때문에
 * 남아 있다. 이것을 끄면 ArchUnit 이 "Layer 'Persistence' is empty" 로 실패한다. 나머지 네 레이어는
 * 이미 채워져 있으므로, {@code @PersistenceAdapter} 를 실제로 쓸지 정의를 지울지 결정되면
 * 이 옵션을 제거해 "레이어가 통째로 비는" 상황까지 게이트로 잡을 수 있다.
 *
 * <p><b>어노테이션 누락 감지(이슈 #40)</b> — 위 레이어 규칙은
 * {@code consideringOnlyDependenciesInLayers()} 라서, 어노테이션이 빠진 클래스는 어느 레이어에도
 * 속하지 않아 <b>조용히 검사에서 제외</b>된다. CLAUDE.md 4장은 "누락 자체가 리뷰 반려 대상"이라
 * 규정하지만 그동안 자동 검증이 없었고, 실제로 이슈 #38 에서 스테레오타입이 빠진 도메인 서비스
 * 두 개가 통과해 컨텍스트 기동 실패로 이어졌다. 아래 규칙들이 그 체크를 사람에게서 게이트로 옮긴다.
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

    // --- 레이어 어노테이션 누락 감지 (이슈 #40) ---

    /** {@code @RestControllerAdvice}(전역 예외 핸들러)는 컨트롤러가 아니므로 이 규칙에 걸리지 않는다. */
    @ArchTest
    static final ArchRule 컨트롤러는_WebAdapter_를_붙인다 = classes()
        .that().areAnnotatedWith(RestController.class)
        .should().beAnnotatedWith(WebAdapter.class);

    @ArchTest
    static final ArchRule 도메인_서비스는_UseCase_를_붙인다 = classes()
        .that().resideInAPackage("..domain..")
        .and().areTopLevelClasses()
        .and().areNotInterfaces()
        .and().haveSimpleNameEndingWith("Service")
        .should().beAnnotatedWith(UseCase.class);

    @ArchTest
    static final ArchRule 포트_구현체는_ExternalAdapter_를_붙인다 = classes()
        .that().resideInAPackage("..infra..")
        .and().implement(resideInAPackage("..domain.port.."))
        .should().beAnnotatedWith(ExternalAdapter.class);

    /**
     * {@code @EngineComponent} 는 스테레오타입이 아니므로(engine 이 Spring 에 의존하면 안 된다)
     * 컴포넌트 스캔 대상이 아니고, {@link EngineConfig} 에 {@code @Bean} 으로 수동 등록해야 한다.
     * 등록을 빠뜨려도 컴파일과 단위 테스트는 전부 통과하고 <b>앱 기동 시점에야</b>
     * {@code NoSuchBeanDefinitionException} 으로 터진다 — 이슈 #38 에서 5개가 누락돼 있었다.
     *
     * <p>{@code ApplicationContextSmokeTest} 는 "실제로 주입되는" 계산기만 잡지만,
     * 이 규칙은 주입처가 아직 없는 계산기까지 포함해 등록 누락을 컴파일 직후에 잡는다.
     */
    @ArchTest
    static final ArchRule engine_계산기는_EngineConfig_에_빈으로_등록된다 = classes()
        .that().areAnnotatedWith(EngineComponent.class)
        .should(등록되어_있어야_한다());

    private static ArchCondition<JavaClass> 등록되어_있어야_한다() {
        Set<String> 등록된_타입 = Arrays.stream(EngineConfig.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Bean.class))
            .map(Method::getReturnType)
            .map(Class::getName)
            .collect(Collectors.toSet());

        return new ArchCondition<>("EngineConfig 에 @Bean 으로 등록되어야 한다") {
            @Override
            public void check(JavaClass 계산기, ConditionEvents events) {
                boolean 등록됨 = 등록된_타입.contains(계산기.getName());
                events.add(new SimpleConditionEvent(계산기, 등록됨,
                    "%s 가 EngineConfig 에 @Bean 으로 등록되어 있지 %s"
                        .formatted(계산기.getSimpleName(), 등록됨 ? "있다" : "않다")));
            }
        };
    }
}
