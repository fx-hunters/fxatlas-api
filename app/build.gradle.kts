plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

dependencies {
    // engine 모듈 의존 — 유일하게 허용되는 컴파일 타임 의존 방향 (app → engine)
    implementation(project(":engine"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // DB / 마이그레이션
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // API 문서 (Swagger UI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 리포지토리 통합 테스트 — 실제 Postgres 컨테이너 + Flyway 마이그레이션 실연동 (이슈 #8)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    // 아키텍처 검증 (ArchUnit) — 문서 4장
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

// 실행 가능한 bootJar 하나만 남긴다(-plain.jar 미생성) — Dockerfile 에서 단일 jar 를 복사하기 위함.
tasks.named<Jar>("jar") {
    enabled = false
}

// Testcontainers 내장 docker-java 는 기본 Docker API 버전(1.32)을 쓴다. 최신 Docker Engine(29+,
// MinAPIVersion 1.40)에서는 400 이 나므로, DOCKER_API_VERSION 이 지정된 경우에만 test JVM 에
// api.version 시스템 프로퍼티로 전달한다. 환경변수가 없는 CI(표준 Docker)에서는 아무 영향이 없다.
tasks.withType<Test>().configureEach {
    System.getenv("DOCKER_API_VERSION")?.let { systemProperty("api.version", it) }
}

