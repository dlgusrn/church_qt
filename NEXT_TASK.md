# NEXT_TASK.md

## 현재 상태 요약

### 1. 운영 전환 준비 상태
- 완료: `scripts/release_preflight.sh` (환경변수/빌드 사전점검)
- 완료: `scripts/staging_smoke.sh` (헬스 JSON 형태 + admin 비노출 + CORS 검증)
- 완료: `scripts/release_verify.sh` (preflight + smoke 원샷)
- 완료: GitHub Actions `release-verify` workflow_dispatch 추가
- 완료: 로컬 검증 `scripts/local_verify.sh` 안정화(중복 기동 방지, 타임아웃 시 로그 출력)

### 2. 운영 반영 직전 체크리스트
- `RELEASE_RUNBOOK.md` 순서대로 점검
- GitHub Secrets 확인:
- `DB_PASSWORD`, `APP_JWT_SECRET`
- 선택: `DB_URL`+`DB_USERNAME` 또는 `DB_HOST`+`DB_NAME`+`DB_USER`
- `release-verify` 워크플로 수동 실행

### 3. 남은 최우선 개발 작업(정식 프론트 분리)
- 관리자 콘솔(test) 의존 제거 계획 확정
- 정식 관리자 프론트에서 `GET /api/admin/bootstrap` 기반 초기 로딩 연동
- 배정/해제/이동 undo UX를 정식 프론트 상태관리로 이관
- 정식 프론트 배포 후 `APP_ADMIN_FRONTEND_ALLOWED_ORIGINS` 운영값 고정

## 구현 방식 선호

- 기존 구조 유지
- DTO는 record
- AdminController / AdminService / 기존 Repository 확장 우선
- 불필요한 리팩토링 금지
- 먼저 동작하는 버전 구현 후 개선
