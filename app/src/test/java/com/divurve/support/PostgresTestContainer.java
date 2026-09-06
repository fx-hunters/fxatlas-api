package com.divurve.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 테스트 전역 공용 Postgres 컨테이너. JVM 당 한 번만 기동하고 모든 테스트가 공유한다.
 *
 * <p>{@code @Container}+{@code @Testcontainers} 를 상속으로 쓰면 클래스마다 컨테이너를 중지·재기동해
 * 다음 클래스에서 연결이 끊기므로 static 초기화 블록 싱글턴을 쓴다. 컨테이너는 JVM 종료 시 Ryuk 이 정리한다.
 *
 * <p>{@code RepositoryTestBase}(@DataJpaTest 슬라이스)와 {@code ApplicationContextSmokeTest}
 * (@SpringBootTest 전체 컨텍스트)가 같은 인스턴스를 공유하므로 컨테이너 기동 비용은 한 번만 든다.
 */
public final class PostgresTestContainer {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        INSTANCE.start();
    }

    private PostgresTestContainer() {
    }

    /** datasource 접속 정보를 테스트 컨텍스트에 주입한다. */
    public static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", INSTANCE::getUsername);
        registry.add("spring.datasource.password", INSTANCE::getPassword);
    }
}
