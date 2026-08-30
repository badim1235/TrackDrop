# TrackDrop MVP Project Baseline

> 상태: **Accepted**
>
> 최근 개정일: 2026-08-30
>
> 제품 정책의 단일 기준 문서다. 다른 문서와 제품 정책이 충돌하면 이 문서를 먼저 확인하고 관련 문서를 함께 갱신한다.

## 1. 제품 정의

TrackDrop은 익명 계정 사용자들이 자신이 좋아하는 음악을 소개하고, 다른 사용자들이 하루에 제한된 표로 지지하여 전체 및 장르별 Daily Chart를 만드는 커뮤니티 기반 Music Discovery Platform이다.

핵심 질문은 다음과 같다.

> 오늘 다른 사람들이 가장 추천하고 싶은 음악은 무엇인가?

개인화 추천 알고리즘보다 사람의 한정된 일일 선택, 한줄평과 날짜별 순위를 중심으로 한다.

## 2. 확정된 제품 정책

| 영역 | 기준 정책 |
| --- | --- |
| 서비스명 | `TrackDrop` |
| 계정 | 사용자가 로그인 ID, 비밀번호와 복구용 이메일을 입력한다. |
| 공개 Identity | 로그인 ID와 이메일은 공개하지 않고 자동 생성된 고유 익명 닉네임만 노출한다. |
| 계정 복구 | 이메일은 MVP에서 수집하지만 이메일 인증, ID 찾기와 비밀번호 재설정은 MVP 이후 구현한다. |
| 일일 추천권 | 신규 Track 소개와 기존 Track Vote를 합산해 사용자당 하루 4회다. UI는 `오늘의 추천 n/4`로 표시한다. |
| 서비스 날짜 | `Asia/Seoul` 기준이며 저장 시각은 UTC instant를 사용한다. |
| 신규 소개 | 외부 카탈로그에서 Track을 선택하고 시스템 장르 하나와 1~120자 한줄평으로 Recommendation을 생성한다. 생성자의 첫 Vote도 같은 트랜잭션에서 생성되고 추천권 1회를 소비한다. |
| 기존 곡 지지 | 등록된 Track에 오늘의 Vote를 생성하며 추천권 1회를 소비한다. |
| 중복 | 같은 사용자는 같은 Track에 같은 날짜로 한 번만 Vote할 수 있다. Track당 활성 Recommendation은 하나다. |
| 홈 | 오늘 Vote가 많은 Track과 최근 등록된 Track을 별도 섹션으로 표시한다. |
| 장르 | `Hip-Hop`, `R&B`, `Rock`, `Indie`, `Electronic`, `Jazz`, `K-Pop`, `J-Pop`, `Pop`, `Other` 순서다. 사용자는 이 목록에서 대표 장르 하나를 선택하며 자유 입력하지 않는다. |
| 차트 | 전체 및 장르별 Daily Chart를 제공하고 추천순 Top 20을 먼저 표시한 뒤 더 보기를 지원한다. |
| 순위 | `ROW_NUMBER`를 사용한다. Vote 수 내림차순, Track 이름의 대소문자를 구분하지 않는 오름차순, 아티스트명 오름차순, Track ID 오름차순으로 고유 순서를 정한다. |
| 오늘 차트 | 오늘의 Vote를 실시간 집계하는 `LIVE` 차트다. |
| 과거 차트 | 자정 배치로 확정한 `DailyRanking` snapshot을 `FINAL` 읽기 전용 목록으로 제공한다. 과거 화면에서는 Vote와 신규 추천을 제공하지 않는다. |
| 음악 검색 | 첫 provider adapter는 Apple iTunes Search API다. 내부 Track ID와 외부 provider ID를 분리하고 가능한 경우 ISRC를 보존한다. |
| 미리듣기 | Apple이 공식 제공한 30초 preview URL만 원본에서 스트리밍한다. 시작 위치는 provider가 선택하므로 인트로라고 보장하지 않는다. |
| 재생 금지 사항 | YouTube player/embed를 사용하지 않고 음원을 다운로드, 절단, 변환, 캐시 또는 재호스팅하지 않는다. |
| 외부 듣기 | Apple이 응답한 외부 Track 링크와 요구되는 Store attribution을 preview 가까이에 표시한다. |
| 한줄평 신고 | 도메인과 API는 구현하지만 feature flag를 기본적으로 끄고 MVP UI에서는 숨긴다. |

## 3. MVP 포함 범위

- 가입, 로그인, 로그아웃과 자동 생성 익명 닉네임
- Apple 음악 검색과 Track 선택·정규화
- 시스템 장르 선택과 한줄평을 포함한 신규 Recommendation
- 기존 Track Vote, 일일 4회 제한과 당일 중복 방지
- 오늘 추천 상위와 최근 등록으로 구성된 홈
- 전체·장르별 오늘 차트와 과거 DailyRanking snapshot
- Apple 공식 30초 preview와 외부 Track 링크
- 반응형 웹 UI
- Ranking snapshot scheduler와 재실행 가능한 batch
- 숨겨진 한줄평 신고 도메인/API
- 핵심 동시성, 장애와 데이터 무결성 자동화 테스트

## 4. MVP 제외 범위

- 이메일 인증, ID 찾기와 비밀번호 재설정 UI·발송 인프라
- Rising, Hidden Gems, 주간·월간·역대 차트
- 댓글, 팔로우, 플레이리스트와 개인화 추천
- Taste Profile, 추천 이력과 사용자 활동 통계
- 닉네임 변경, Vote 취소와 한줄평 수정·삭제
- 복수 음악 provider 동시 검색
- 모든 사용자에게 0초부터 시작하는 인트로 재생 보장
- Apple Music 구독자 인증을 사용하는 MusicKit 전체 곡 재생
- 관리자 UI와 실제 신고 검토·제재 화면
- Redis 필수 도입과 별도 Batch/Worker 서비스

## 5. 핵심 불변식

1. Recommendation 생성, 최초 Vote 생성과 일일 quota 소비는 하나의 트랜잭션이다.
2. Vote 생성과 일일 quota 소비는 하나의 트랜잭션이다.
3. 사용자·Track·서비스 날짜 조합의 Vote는 하나만 존재한다.
4. 사용자·서비스 날짜별 성공한 Recommendation과 Vote 합계는 4회를 초과하지 않는다.
5. 외부 provider Track 하나는 내부에서 중복 생성되지 않는다.
6. Ranking의 원장은 Vote이며 누적 카운터를 정확성의 원천으로 사용하지 않는다.
7. 완료되지 않은 Ranking snapshot은 과거 확정 차트로 공개하지 않는다.
8. 공개 응답과 로그에 로그인 ID, 이메일, 비밀번호 또는 provider credential을 노출하지 않는다.
9. preview 부재나 외부 provider 장애가 기존 Track 조회, Vote와 과거 차트 조회를 막지 않는다.

## 6. 분야별 상세 문서

- 화면과 행동: [`product/user-flows.md`](product/user-flows.md)
- 데이터 모델: [`architecture/erd.md`](architecture/erd.md)
- 기술 스택 제안: [`architecture/tech-stack.md`](architecture/tech-stack.md)
- REST API: [`api/rest-api.md`](api/rest-api.md)
- 기술 결정: [`architecture/adr/`](architecture/adr/)
- 초기 요구사항 이력: [`history/phase-01-mvp-requirements.md`](history/phase-01-mvp-requirements.md)

## 7. 확정된 기술 기준

Phase 5 기술 스택은 [`architecture/tech-stack.md`](architecture/tech-stack.md)에서 관리한다. Java 25, Spring Boot 4.1, PostgreSQL 18, React 19.2, Vite 8과 OpenAPI 3.1 기반 모듈형 모놀리스로 확정했다.

기술 선택의 배경과 trade-off는 [`architecture/adr/0002-modular-monolith-stack.md`](architecture/adr/0002-modular-monolith-stack.md)에 보존한다. 정확한 patch version과 transitive dependency는 Phase 6에서 생성하는 Maven과 npm lock에 고정한다. 배포 provider는 가격과 운영 조건을 확인해야 하므로 Phase 14에서 선택한다.

## 8. 변경 규칙

제품 정책을 바꿀 때는 이 문서를 먼저 수정하고 영향을 받는 user flow, ERD, API, ADR와 테스트를 같은 작업에서 갱신한다. 과거 Phase 문서는 결정 이력이므로 새로운 기준 정책을 추가하는 장소로 사용하지 않는다.
