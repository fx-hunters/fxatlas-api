package com.divurve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.divurve.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.divurve.engine.bucket.BucketAllocator;
import com.divurve.engine.cost.CostCalculator;
import com.divurve.engine.safemode.SafeModeEvaluator;
import com.divurve.engine.simulate.MonteCarloSimulator;
import com.divurve.engine.split.SplitVarianceReducer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 전체 Spring 컨텍스트 기동 스모크 테스트 (이슈 #38).
 *
 * <p>이 테스트가 잡는 것 — 단위 테스트로는 원리적으로 잡을 수 없는 기동 시점 결함들이다.
 * <ul>
 *   <li>engine 계산기의 {@code EngineConfig} 빈 등록 누락 → {@code NoSuchBeanDefinitionException}.
 *       {@code @EngineComponent} 는 스테레오타입이 아니라 컴포넌트 스캔 대상이 아니므로
 *       수동 등록이 필요한데, 누락돼도 컴파일·단위 테스트는 전부 통과한다.</li>
 *   <li>Flyway 마이그레이션 버전 중복 → {@code FlywayException}.
 *       병렬 브랜치가 같은 버전 번호를 쓰면 발생한다.</li>
 *   <li>컨트롤러 간 경로 중복 → {@code IllegalStateException: Ambiguous mapping}.
 *       스텁 컨트롤러를 지우지 않고 실구현 컨트롤러를 새로 만들면 발생한다.</li>
 *   <li>엔티티-스키마 불일치 → {@code ddl-auto: validate} 가 기동 시 검증한다.</li>
 * </ul>
 *
 * <p>2026-09-06 develop 대규모 CI 실패에서 위 세 가지가 <b>모두 동시에</b> 실재했으나,
 * 컨텍스트를 띄우는 테스트가 {@code @DataJpaTest} 슬라이스뿐이어서 CI 가 하나도 잡지 못했다.
 *
 * <p>컨텍스트 캐시가 갈라지면 CI 시간이 배로 늘어나므로 {@code @SpringBootTest} 는 이 클래스
 * 하나로 유지한다. 프로퍼티 오버라이드나 {@code @MockBean} 을 여기에 추가하지 말 것.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationContextSmokeTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerDatasource(registry);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 전체_컨텍스트가_기동된다() {
        assertThat(context).isNotNull();
    }

    /**
     * 생성자 주입 대상인 engine 계산기가 빠짐없이 빈으로 등록되었는지 확인한다.
     * 이 5개는 이슈 #38 이전에 실제로 등록이 누락돼 있었다 — 앱이 기동 자체를 못 하는 상태였다.
     */
    @Test
    void 주입_대상_engine_계산기가_모두_빈으로_등록된다() {
        assertThat(context.getBean(BucketAllocator.class)).isNotNull();
        assertThat(context.getBean(SplitVarianceReducer.class)).isNotNull();
        assertThat(context.getBean(CostCalculator.class)).isNotNull();
        assertThat(context.getBean(MonteCarloSimulator.class)).isNotNull();
        assertThat(context.getBean(SafeModeEvaluator.class)).isNotNull();
    }

    /**
     * 컨트롤러 매핑이 등록되었는지 확인한다.
     * 경로가 중복되면 이 빈을 만드는 시점에 ambiguous mapping 으로 기동이 실패하므로,
     * 주입에 성공했다는 것 자체가 중복 매핑이 없다는 증거다.
     */
    @Test
    void 컨트롤러_매핑이_중복_없이_등록된다() {
        assertThat(handlerMapping.getHandlerMethods()).isNotEmpty();
    }

    /**
     * OpenAPI 문서가 {@code userId} 를 요청 파라미터로 광고하지 않는지 확인한다 (이슈 #50).
     *
     * <p>{@code @CurrentUser} 는 커스텀 리졸버가 채우는 파라미터라, springdoc 에 알려주지 않으면
     * 모든 보호 엔드포인트에 {@code userId} 쿼리 파라미터가 있는 것처럼 문서가 만들어진다 —
     * 이슈 #50 에서 제거한 취약한 형태를 문서가 프론트에 다시 권하게 된다.
     * {@code OpenApiConfig} 가 그 어노테이션을 문서 생성에서 제외한다.
     */
    @Test
    void OpenAPI_문서가_userId_를_요청_파라미터로_노출하지_않는다() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode paths = new ObjectMapper().readTree(apiDocs).path("paths");
        assertThat(paths).isNotEmpty();

        List<String> 노출된_userId_파라미터 = new ArrayList<>();
        paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(operation -> {
            for (JsonNode parameter : operation.getValue().path("parameters")) {
                if ("userId".equals(parameter.path("name").asText())
                        || "user_id".equals(parameter.path("name").asText())) {
                    노출된_userId_파라미터.add(path.getKey() + " " + operation.getKey());
                }
            }
        }));

        assertThat(노출된_userId_파라미터).isEmpty();
    }
}
