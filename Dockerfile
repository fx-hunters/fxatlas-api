# syntax=docker/dockerfile:1

# --- 빌드 스테이지: Gradle 로 app 모듈의 bootJar 생성 (Render 는 Java 네이티브 미지원 → Docker 런타임) ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Gradle wrapper 와 빌드 스크립트 먼저 복사 (의존성 캐시 레이어 활용)
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

# 모듈 소스 복사
COPY engine ./engine
COPY app ./app

# 테스트는 CI 에서 돌리므로 이미지 빌드에서는 제외(-x test) — bootJar 만 생성
RUN ./gradlew :app:bootJar --no-daemon -x test

# --- 런타임 스테이지: JRE 만 포함한 경량 이미지 ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# app 모듈 bootJar 복사 (plain.jar 는 비활성화되어 있어 단일 jar 만 존재)
COPY --from=build /workspace/app/build/libs/*.jar app.jar

# Render 가 PORT 환경변수를 주입하면 application.yml 의 server.port 가 이를 사용한다.
ENTRYPOINT ["java", "-jar", "app.jar"]
