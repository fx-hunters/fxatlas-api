# divurve-api

Divurve 백엔드. 외화 목표·환전 타이밍 계산 엔진을 전담한다.
작업 규칙은 [CLAUDE.md](CLAUDE.md) 를 따른다.

## 요구사항

| 항목 | 버전 |
| --- | --- |
| JDK | 17 (Gradle toolchain 이 고정) |
| Docker | 실행 중일 것 — 리포지토리 통합 테스트가 Testcontainers 로 Postgres 를 띄운다 |

## 로컬 검증 — push 전에 반드시

CI(`.github/workflows/ci.yml`)와 **같은 검증**을 한 번에 돌린다.

```bash
export DOCKER_API_VERSION=1.44
./gradlew ciCheck
```

`ciCheck` = `build` + `test` + `jacocoTestCoverageVerification`.
여기에 ArchUnit 레이어·모듈 검증(문서 4장)과 JaCoCo 라인·브랜치·인스트럭션 100% 게이트(문서 8장)가 모두 포함된다.
**로컬에서 통과하면 CI 에서도 통과한다** — 반대로 이걸 건너뛰고 push 하면 CI 가 대신 실패한다.

### `DOCKER_API_VERSION` 이 왜 필요한가

Testcontainers 내장 docker-java 는 Docker API 버전을 기본값(1.32)으로 요청한다.
Docker Engine 29 이상은 최소 지원 API 가 1.40 이라 이 요청을 400 으로 거절하고, 컨테이너가 뜨지 않아 통합 테스트가 전부 실패한다.

`DOCKER_API_VERSION` 을 지정하면 `app/build.gradle.kts` 가 이를 테스트 JVM 의 `api.version` 시스템 프로퍼티로 넘겨 협상 버전을 맞춘다.
GitHub Actions 러너의 Docker 는 아직 구버전이라 이 변수 없이 동작하므로, CI 에는 설정하지 않는다.

셸을 새로 열 때마다 지정하는 게 번거로우면 `~/.zshrc` 에 넣어 둔다.

```bash
echo 'export DOCKER_API_VERSION=1.44' >> ~/.zshrc
```

### 커버리지 미달일 때

게이트 실패 로그는 `Rule violated for bundle app: branches covered ratio is 0.99, ...` 한 줄뿐이라 어느 클래스인지 알 수 없다.
미커버 지점은 HTML 리포트에서 확인한다.

```bash
open app/build/reports/jacoco/test/html/index.html
```

CI 에서 실패한 경우 같은 리포트가 워크플로 실행 페이지의 **Artifacts → `reports-<run_id>-<attempt>`** 로 올라온다.

## 애플리케이션 실행

```bash
./gradlew :app:bootRun --args='--spring.profiles.active=dev'
```

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI 스펙: http://localhost:8080/v3/api-docs

## 모듈 구조

| 모듈 | 역할 |
| --- | --- |
| `engine` | 계산 순수 로직. Spring·JPA 의존 금지. 커버리지 100% |
| `app` | 나머지 전부 — `common` / `domain` / `infra` / `api` 를 패키지로 구분 |

상세는 [CLAUDE.md](CLAUDE.md) 3장 참고.
