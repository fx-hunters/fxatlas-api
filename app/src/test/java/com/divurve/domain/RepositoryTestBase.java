package com.divurve.domain;

import com.divurve.support.PostgresTestContainer;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 리포지토리 통합 테스트 공용 베이스. 실제 Postgres 컨테이너에 Flyway 마이그레이션을 적용해
 * 엔티티 매핑·스키마·소유자 필터(NFR-SE-03)를 실연동으로 검증한다.
 *
 * <p>컨테이너는 {@link PostgresTestContainer} 가 JVM 당 하나만 기동해 공유한다 —
 * {@code ApplicationContextSmokeTest} 와 같은 인스턴스를 쓰므로 기동 비용이 중복되지 않는다.
 * {@code replace = NONE} 으로 임베디드 DB 대체를 막아 실제 Postgres + Flyway 를 쓴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class RepositoryTestBase {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerDatasource(registry);
    }
}
