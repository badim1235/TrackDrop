# TrackPick

TrackPick은 사용자가 좋아하는 곡을 장르별로 소개하고, 하루 네 번의 추천권으로 오늘의 차트를 함께 만드는 음악 발견 서비스입니다.

## 기술 구성

- Backend: Java 25, Spring Boot 4.1, Spring Security, JPA, JdbcClient
- Frontend: React 19, TypeScript, Vite 8, TanStack Query
- Identity: Supabase Auth 이메일 인증
- Database: Supabase PostgreSQL, Flyway (로컬 개발은 PostgreSQL 18)
- Contract: OpenAPI 3.1

## 현재 구현 상태

Phase 12까지 구현되어 계정 인증부터 Apple 음악 검색, 곡 추천, 오늘의 실시간 차트와 홈 음악 발견 피드까지 사용할 수 있습니다.

- 이메일과 비밀번호를 사용하는 Supabase 회원가입·로그인
- 가입 확인 메일과 비밀번호 재설정 메일 요청
- 고유한 한국어 익명 닉네임 자동 생성
- PostgreSQL server session 기반 로그인·로그아웃과 CSRF 보호
- 여러 기기 동시 로그인과 선택형 7일 로그인 유지
- 내 계정 이메일과 오늘의 추천권 조회
- IP별 인증 요청 및 계정 생성 제한
- KR 스토어 기준 Apple 음악 카탈로그 검색과 관련도순 상위 20곡 표시
- Explicit 곡 포함·표시, 앨범 정보와 Apple Music 외부 링크 제공
- 검색 메타데이터 캐시, Apple 호출 제한과 외부 서비스 오류 처리
- Apple Music 최상위 카탈로그 장르 목록과 곡별 원본 장르 기본 제안
- 대표 장르와 1~120자 한줄평을 포함한 신규 곡 등록
- 등록과 동시에 최초 Vote 생성 및 오늘의 추천권 1회 원자적 차감
- 동일 Apple Track 중복 등록 방지와 하루 최대 4회 동시성 보호
- 검색 결과에서 기존 Track에 오늘 Vote하고 추천권을 즉시 갱신
- Track별 당일 중복 Vote 방지와 한도 초과 시 Vote·추천권 원자적 rollback
- 오늘 전체·장르별 실시간 차트와 `ROW_NUMBER` 고유 순위
- 득표수·곡명·아티스트·Track ID 기준 결정적 정렬
- 추천순 20곡과 고정 시점 cursor 기반 더 보기
- 차트에서 기존 Track 추천 및 추천권·순위 즉시 갱신
- 차트 곡별 Apple 30초 미리듣기와 외부 전체 듣기 링크
- 홈의 오늘 추천 상위 6곡과 최근 등록 6곡 실시간 표시
- 최근 등록 전체 목록과 고정 시점 cursor 기반 더 보기
- 홈·최근 목록의 미리듣기, Apple 링크와 추천권 연동

과거 확정 차트와 자정 Ranking snapshot은 다음 Phase에서 구현합니다.

## 로컬 실행

필수 도구는 Java 25, Node.js 24, Docker Desktop과 Supabase 프로젝트입니다. Supabase Dashboard의 Email provider에서 `Confirm email`을 켜고 URL Configuration에 `http://127.0.0.1:5173/**`를 Redirect URL로 추가합니다.

```powershell
docker compose up -d postgres

cd backend
$env:SUPABASE_URL="https://your-project-ref.supabase.co"
$env:SUPABASE_PUBLISHABLE_KEY="sb_publishable_your_key"
.\mvnw.cmd spring-boot:run

cd ..\frontend
npm install
npm run dev
```

프런트엔드는 `http://localhost:5173`, API는 `http://localhost:8080`에서 실행됩니다. Vite 개발 서버가 `/api` 요청을 백엔드로 전달합니다.

운영 환경에서는 Spring datasource를 Supabase PostgreSQL 연결 정보로 설정하고 `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `AUTH_EMAIL_REDIRECT_URL`, `AUTH_PASSWORD_RECOVERY_REDIRECT_URL`, `IP_HASH_SECRET`, `SESSION_COOKIE_SECURE=true`를 별도로 설정합니다. 실제 이메일 발송에는 Supabase Custom SMTP 설정을 권장합니다. Docker Desktop이 실행 중이어야 로컬 PostgreSQL과 Testcontainers 기반 통합 테스트를 사용할 수 있습니다.

## Render 배포

루트의 `render.yaml`은 싱가포르 리전의 무료 Docker Web Service 하나에 프런트엔드와 백엔드를 함께 배포합니다. Render Blueprint 생성 화면에서 저장소를 연결하고 다음 값만 입력합니다.

- `DATABASE_URL`: Supabase Session pooler의 JDBC URL (`?sslmode=require` 포함)
- `POSTGRES_USER`: Supabase Session pooler 사용자
- `POSTGRES_PASSWORD`: Supabase 데이터베이스 비밀번호
- `SUPABASE_URL`: Supabase 프로젝트 URL
- `SUPABASE_PUBLISHABLE_KEY`: Supabase publishable key

Render가 제공하는 `RENDER_EXTERNAL_URL`을 가입 확인과 비밀번호 재설정의 기본 리디렉션 주소로 사용합니다. 배포가 완료되면 Supabase Authentication의 URL Configuration에서 Site URL을 Render의 HTTPS 주소로 설정하고 다음 Redirect URL을 추가합니다.

```text
https://<render-host>/login?verified=1
https://<render-host>/recover/password
```

로컬 개발을 계속 사용하려면 기존 `http://127.0.0.1:5173/**` Redirect URL도 유지합니다. Render는 HTTPS 인증서를 자동으로 관리하며 운영 쿠키에는 `Secure` 속성이 적용됩니다.

## 검증

```powershell
cd backend
.\mvnw.cmd verify

cd ..\frontend
npm run check
```

설계 기준과 단계별 결정은 [`docs/README.md`](docs/README.md)에서 확인할 수 있습니다.

## 라이선스

[MIT](LICENSE)
