---
name: "✨ Feature"
about: "새 기능 / 작업 단위 이슈"
title: "[feat] "
labels: ["feature"]
assignees: []
---

## 배경 / 목적
<!-- 왜 이 작업이 필요한가? 어떤 문제를 해결하는가 -->

## 작업 범위
<!-- 이 이슈에서 다룰 것 / 다루지 않을 것 -->
- [ ]
- [ ]

## 구현 체크리스트
- [ ] 레이어 어노테이션(@WebAdapter/@UseCase/@ExternalAdapter/@PersistenceAdapter/@EngineComponent) 부착
- [ ] 계산 로직은 engine 모듈(순수 함수)로 — LLM/외부 API 가 수치를 생성하지 않는다
- [ ] 테스트 작성 (engine·domain·infra 서비스 로직은 커버리지 100%)
- [ ] API 추가 시 `/api/v1/...` 경로 + data/meta 응답 래핑 + Swagger 반영

## 브랜치
<!-- 컨벤션 2.1: feat/<scope> · fix/<scope> · chore/<scope> -->
`feat/<scope>`

## 참고
<!-- 관련 이슈/문서/화면 등 -->
