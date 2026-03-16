# 배포/환경변수 가이드

## 1) 필수 환경변수
- `DB_PASSWORD`
- `APP_JWT_SECRET`
- DB 접속값은 아래 둘 중 하나:
  - 조합 A: `DB_URL` + `DB_USERNAME`
  - 조합 B: `DB_HOST` + `DB_NAME` + `DB_USER` (preflight가 `DB_URL`, `DB_USERNAME`으로 자동 변환)

## 2) 선택 환경변수
- `APP_ACCESS_TOKEN_EXPIRATION_SECONDS` (기본: `28800`)
- `APP_ADMIN_CONSOLE_ENABLED` (기본: `false`)
- `APP_ADMIN_FRONTEND_ALLOWED_ORIGINS` (콤마 구분)
- `APP_APP_FRONTEND_ALLOWED_ORIGINS` (콤마 구분, `/api/students/**`, `/api/teacher/me/**`, `/api/teacher/check` CORS)

## 3) 로컬 개발 예시
`.env.example` 복사 후 `.env` 생성:
- `cp .env.example .env`
- 로컬 값으로 수정
- IntelliJ 실행 시에도 `.env`를 자동 로드하도록 구현됨(운영 환경변수 우선).

## 4) 운영 권장값
- `APP_ADMIN_CONSOLE_ENABLED=false`
- `APP_ADMIN_FRONTEND_ALLOWED_ORIGINS=https://admin.your-domain.com`
- `APP_APP_FRONTEND_ALLOWED_ORIGINS=https://app.your-domain.com`
- `APP_JWT_SECRET`는 길고 랜덤한 값 사용
- DB 계정은 최소 권한 원칙 적용

## 5) 정식 프론트 분리 시 체크
1. 백엔드 CORS Origin이 프론트 도메인과 일치하는지
2. 로그인/토큰 저장 방식이 `Bearer` 규격을 따르는지
3. CSV 다운로드 시 `Content-Disposition` 헤더 접근 가능한지
4. `/admin` 및 `/admin.html`가 운영에서 404인지
5. `/ops` 및 `/ops.html`가 운영에서 404인지

## 6) CI 파이프라인
- 파일: `.github/workflows/backend-ci.yml`
- 트리거:
  - `main`, `develop` 브랜치 push
  - `main`, `develop` 대상 pull request
- 실행 내용:
  1. JDK 21 세팅
  2. `./gradlew test --no-daemon`

## 6-1) 릴리즈 사전점검(권장)
- 스크립트: `scripts/release_preflight.sh`
- 체크 항목:
  1. 필수 환경변수 누락 여부
  2. `APP_ADMIN_CONSOLE_ENABLED=false` 여부
  3. `APP_ADMIN_FRONTEND_ALLOWED_ORIGINS` 설정 여부
  4. `APP_JWT_SECRET` 길이(32자 이상)
  5. `./gradlew build --no-daemon` 성공 여부

## 6-2) 릴리즈 원샷 검증(권장)
- 스크립트: `scripts/release_verify.sh`
- 실행:
  - `./scripts/release_verify.sh https://api-staging.example.com https://admin-staging.example.com`
- 동작:
  1. `release_preflight.sh` 실행
  2. `staging_smoke.sh` 실행

## 7) 스테이징 리허설(수동)
- 워크플로: `.github/workflows/staging-smoke.yml`
- 실행 입력:
  - `base_url`: 스테이징 백엔드 URL
  - `admin_origin`: 스테이징 관리자 프론트 Origin
- 체크 항목:
  1. `/health` 200
  2. `/db-health` 200
  3. `/admin`, `/admin.html` 404 (정적 콘솔 비노출)
  4. `/api/admin/years` CORS preflight 허용 확인

## 7-1) 릴리즈 원샷(수동 워크플로)
- 워크플로: `.github/workflows/release-verify.yml`
- 실행 입력:
  - `base_url`: 검증 대상 백엔드 URL
  - `admin_origin`: 허용 관리자 프론트 Origin
- 필요 Secret:
  - `DB_PASSWORD`, `APP_JWT_SECRET`
  - 선택: `DB_URL` + `DB_USERNAME` 또는 `DB_HOST` + `DB_NAME` + `DB_USER`

## 7-1) 로컬 스모크(개발용)
- 스크립트: `scripts/local_smoke.sh`
- 기본 실행:
  - `./scripts/local_smoke.sh`
- 인자 실행:
  - `./scripts/local_smoke.sh http://localhost:8080 http://localhost:5173`
- 선택값:
  - `EXPECT_ADMIN_STATUS` (기본 `404`)
  - 예: `EXPECT_ADMIN_STATUS=200 ./scripts/local_smoke.sh` (로컬에서 admin 콘솔 노출 테스트 시)

## 7-2) 로컬 원샷 검증(기동+스모크)
- 스크립트: `scripts/local_verify.sh`
- 기본 실행:
  - `./scripts/local_verify.sh`
- 동작:
  1. `./gradlew bootRun --no-daemon` 백그라운드 기동
  2. `/health` 200 대기
  3. `local_smoke.sh` 실행
  4. 종료 시 앱 프로세스 자동 정리
- 참고:
  - 이미 `BASE_URL/health`가 200이면 앱 중복 기동을 건너뜀
  - 기동 타임아웃 시 `/tmp/church-qt-local-verify.log` 마지막 로그를 자동 출력
- 옵션:
  - `START_APP=false ./scripts/local_verify.sh` (이미 기동된 앱 검증만 수행)

## 8) 운영 전환 런북
- 파일: `RELEASE_RUNBOOK.md`
- 사전점검 -> 배포 -> 스모크 -> 안전조건 확인 순서로 실행
