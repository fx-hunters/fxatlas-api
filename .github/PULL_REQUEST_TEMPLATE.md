## 개요
<!-- 이 PR 이 무엇을 하는지 한두 문장으로 -->

Closes #

## 변경 사항
<!-- 주요 변경점을 항목으로 -->
-

## 체크리스트
- [ ] **`./gradlew ciCheck` 로컬 통과** (`export DOCKER_API_VERSION=1.44` 선행 — README 참고)
- [ ] **최신 `develop` 을 머지/리베이스한 상태에서 검증** — 오래된 베이스의 green 체크는 근거가 되지 않는다
- [ ] 커밋 컨벤션 준수 (타입/스코프/한 줄 요약 + 본문)
- [ ] 새 클래스에 레이어 어노테이션 부착
- [ ] ArchUnit 아키텍처 검증 통과 (LayerArchitectureTest · ModuleArchitectureTest)
- [ ] 테스트 커버리지 100% (JaCoCo, 인스트럭션·라인·브랜치) — CI 통과로 확인
- [ ] API 변경 시 Swagger/`/v3/api-docs` 반영, 브레이킹 체인지는 `/api/v2` 신설
- [ ] 계산 로직 변경 시: 커밋 타입 `calc` + 본문에 변경 전/후 수치 기록

## 참고
<!-- 스크린샷, 관련 이슈/문서 -->
