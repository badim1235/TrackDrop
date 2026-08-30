# TrackDrop

TrackDrop은 사용자가 좋아하는 곡을 장르별로 소개하고, 하루 네 번의 추천권으로 오늘의 차트를 함께 만드는 음악 발견 서비스입니다.

## 기술 구성

- Backend: Java 25, Spring Boot 4.1, Spring Security, JPA, JdbcClient
- Frontend: React 19, TypeScript, Vite 8, TanStack Query
- Database: PostgreSQL 18, Flyway
- Contract: OpenAPI 3.1

## 로컬 실행

필수 도구는 Java 25, Node.js 24, Docker Desktop입니다.

```powershell
docker compose up -d postgres

cd backend
.\mvnw.cmd spring-boot:run

cd ..\frontend
npm install
npm run dev
```

프런트엔드는 `http://localhost:5173`, API는 `http://localhost:8080`에서 실행됩니다. Vite 개발 서버가 `/api` 요청을 백엔드로 전달합니다.

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
