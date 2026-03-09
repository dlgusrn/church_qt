# 관리자 프론트 분리 이관 가이드

## 목적
- 현재 `src/main/resources/static/admin.*`는 테스트/운영 검증용 콘솔이다.
- 정식 관리자 UI는 별도 프론트 프로젝트로 분리 배포한다.

## 백엔드 기준 엔드포인트
- 인증: `POST /api/teacher/login`
- 관리자 API: `/api/admin/**`
- CSV 다운로드: `GET /api/admin/audit-logs.csv`

## Bootstrap 스키마 버전
- `GET /api/admin/bootstrap` 응답에 `schemaVersion` 필드 포함
- 현재 값: `v1`
- 정식 프론트는 초기 로딩 시 `schemaVersion`을 확인하고, 예상 버전과 다르면 안전하게 경고/차단 처리 권장
- `generatedAt`(UTC ISO-8601) 필드도 포함되며, 프론트에서 초기 로딩 시점 표시/로그 추적에 활용 가능
- bootstrap 로그 필터 파라미터는 `auditActorTeacherId`, `auditActionType`, `auditKeyword`, `auditFromAt`, `auditToAt`를 지원
- `bootstrap.audit.actorTeacherId`로 서버 적용값을 확인 가능
- 예시: `GET /api/admin/bootstrap?auditLimit=100&auditOffset=0&auditActorTeacherId=12&auditActionType=MOVE_STUDENT`

## 인증 방식
- 로그인 응답의 `accessToken` 사용
- 모든 관리자 API 헤더:
  - `Authorization: Bearer {accessToken}`

## CORS
- 설정 키: `app.admin-frontend.allowed-origins`
- 기본 허용 Origin:
  - `http://localhost:5173`
  - `http://localhost:3000`
- CSV 다운로드를 위해 `Content-Disposition` 헤더가 노출됨

## 정적 콘솔 노출 제어
- 설정 키: `app.admin-console.enabled`
- 기본값: `false`
- `false`일 때 차단:
  - `/admin`
  - `/admin.html`
  - `/admin.js`
  - `/admin.css`

## 권장 프론트 이관 순서
1. 로그인/토큰 저장 구현
2. 연도/반 조회/수정/배정 API 연동
3. audit-logs 조회/CSV 연동
4. undo UX를 프론트 상태관리로 대체
