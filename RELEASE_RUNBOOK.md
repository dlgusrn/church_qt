# RELEASE_RUNBOOK.md

## 목적
운영 반영 전 필수 점검과 배포 후 검증을 빠르게 수행하기 위한 실행 순서.

## 1) 배포 전(로컬/CI)
0. 로컬 운영 유사 실행(선택)
   - `./scripts/run_local_prod_like.sh 8080 http://localhost:5173`
   - 목적: `/admin` 404 + CORS 설정을 운영과 유사하게 고정한 상태로 로컬 검증
0.5. 로컬 릴리즈 리허설(선택)
   - `START_APP=false ./scripts/local_release_rehearsal.sh http://localhost:8080 http://localhost:5173`
   - 목적: `release_preflight + local_verify + app route verify`를 한 번에 실행
1. 환경변수 주입
   - 필수: `DB_PASSWORD`, `APP_JWT_SECRET`
   - DB 접속값은 아래 둘 중 하나
   - 조합 A: `DB_URL`, `DB_USERNAME`
   - 조합 B: `DB_HOST`, `DB_NAME`, `DB_USER` (`release_preflight.sh`가 `DB_URL`/`DB_USERNAME`으로 자동 변환)
   - `APP_ADMIN_CONSOLE_ENABLED=false`
   - `APP_ADMIN_FRONTEND_ALLOWED_ORIGINS=https://admin.your-domain.com`
2. 사전 점검 실행
   - `./scripts/release_preflight.sh`
3. 원샷 검증(권장)
   - `./scripts/release_verify.sh https://api-staging.example.com https://admin-staging.example.com`
3.5. 운영 최종 검증(권장)
   - `./scripts/prod_go_live_check.sh https://api.example.com https://admin.example.com`
   - 특징: `https`/실도메인 강제 + `release_preflight + staging_smoke` 원샷
4. GitHub Actions 대안(권장)
   - `release-verify` workflow_dispatch 실행
   - 입력: `base_url`, `admin_origin`

## 2) 배포
1. 애플리케이션 배포 실행(운영 표준 절차)
2. 앱 기동 후 `/health`, `/db-health` 확인

## 3) 배포 후 스모크 테스트
1. 수동 스크립트 실행
   - `./scripts/staging_smoke.sh https://api-staging.example.com https://admin-staging.example.com`
2. 또는 GitHub Actions `staging-smoke` workflow_dispatch 실행

## 4) 운영 안전 조건
1. `/admin`, `/admin.html`는 404여야 함
2. 관리자 분리 프론트 Origin만 CORS 허용
3. 관리자 API는 JWT + ADMIN 권한 검증 통과 필요

## 5) 문제 시 롤백 기준
1. `/health` 또는 `/db-health` 비정상
2. 관리자 API 5xx 급증
3. CORS 오동작(허용 Origin 불일치)
