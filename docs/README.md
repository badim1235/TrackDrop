# TrackDrop 문서 안내

이 디렉터리는 TrackDrop의 제품 정책, 화면 흐름, 데이터 설계, API 계약과 결정 이력을 관리한다. Phase는 작업 순서를 나타내지만, 구현 중 사용하는 문서의 구조는 책임 영역을 기준으로 한다.

## 문서 지도

| 문서 | 역할 | 상태 |
| --- | --- | --- |
| [`00-project-baseline.md`](00-project-baseline.md) | 서비스명, 추천권, 장르, 랭킹, preview 등 확정된 MVP 제품 정책의 단일 기준 | Accepted |
| [`product/user-flows.md`](product/user-flows.md) | 화면 구조, 사용자 행동, UI 상태와 오류 복구 | Accepted |
| [`architecture/erd.md`](architecture/erd.md) | Entity, 관계, DB 제약, 트랜잭션과 Ranking snapshot | Accepted |
| [`architecture/tech-stack.md`](architecture/tech-stack.md) | Phase 5 언어, framework, DB, 보안, 테스트와 배포 단위 | Accepted |
| [`api/rest-api.md`](api/rest-api.md) | HTTP endpoint, request/response, 오류와 pagination 계약 | Proposed |
| [`architecture/adr/`](architecture/adr/) | 중요한 기술 선택의 배경, 결정과 결과 | 결정별 상태 |
| [`history/`](history/) | Phase 진행 당시의 분석과 결정 기록 | Historical |

## 기준 문서 원칙

같은 내용을 여러 문서에 독립적으로 확정하지 않는다.

| 변경하려는 내용 | 먼저 수정할 기준 |
| --- | --- |
| 제품 정책과 MVP 범위 | `00-project-baseline.md` |
| 사용자에게 보이는 화면과 행동 | `product/user-flows.md` |
| 저장 구조와 데이터 무결성 | DB migration과 `architecture/erd.md` |
| 공개 HTTP 계약 | OpenAPI 도입 전에는 `api/rest-api.md`, 도입 후에는 `openapi.yaml` |
| 기술 선택과 변경 이유 | `architecture/adr/` |
| 실제로 검증된 동작 | 자동화 테스트 |

문서와 구현이 충돌하면 한쪽을 조용히 정답으로 간주하지 않는다. 제품 의도는 baseline에서 확인하고, migration, OpenAPI, 테스트와 관련 문서를 같은 변경에서 함께 맞춘다.

## 변경 절차

1. 제품 정책 변경이면 baseline을 먼저 수정한다.
2. 영향받는 user flow, ERD, API 계약을 함께 찾는다.
3. 되돌리기 어렵거나 대안이 있는 기술 선택은 ADR을 추가한다.
4. 구현 단계에서는 migration, OpenAPI, 테스트를 같은 변경에 포함한다.
5. Phase 이력 문서는 당시 기록이므로 오탈자나 잘못된 링크 외에는 다시 기준 문서처럼 갱신하지 않는다.

## 로드맵 상태

| Phase | 작업 | 상태 |
| ---: | --- | --- |
| 1 | MVP 요구사항 | 완료, history 보존 |
| 2 | 화면 및 User Flow | 완료, living document로 전환 |
| 3 | ERD | 완료, living document로 전환 |
| 4 | REST API | 초안 완료, 최종 승인 대기 |
| 5 | 기술 스택 결정 | 완료 |
| 6 | 프로젝트 초기 구조 생성 | 다음 작업 |
| 7~15 | 인증부터 배포·포트폴리오 문서까지 | 대기 |
