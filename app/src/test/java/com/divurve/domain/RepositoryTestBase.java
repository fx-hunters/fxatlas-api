package com.divurve.domain;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 리포지토리 통합 테스트 공용 베이스. 실제 Postgres 컨테이너에 Flyway 마이그레이션을 적용해
 * 엔티티 매핑·스키마·소유자 필터(NFR-SE-03)를 실연동으로 검증한다.
 *
 * <p>컨테이너는 싱글톤 패턴으로 static 초기화 블록에서 한 번만 기동하고, 모든 하위 테스트가 공유한다.
 * ({@code @Container}+{@code @Testcontainers} 를 상속으로 쓰면 클래스마다 컨테이너를 중지·재기동해
 * 다음 클래스에서 연결이 끊기므로 이 방식을 쓴다. 컨테이너는 JVM 종료 시 Ryuk 이 정리한다.)
 * {@code @DynamicPropertySource} 로 datasource 접속 정보를 주입하고,
 * {@code replace = NONE} 으로 임베디드 DB 대체를 막아 실제 Postgres + Flyway 를 쓴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class RepositoryTestBase {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
