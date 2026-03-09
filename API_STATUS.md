# API_STATUS.md

## 1. 학생 API

### 완료
- GET /health
- GET /db-health
- GET /api/students
- GET /api/students/{studentId}/calendar?year=2026&month=3

### 동작 설명
- 학생 목록은 최신 공개 연도(is_open_to_students = true, is_active = true) 기준으로 편성된 학생만 조회
- 학생 달력은 연도/월 기준 날짜별 qtChecked, noteChecked, isToday 반환
- summary는 해당 연도의 전체 누적 개수 기준

---

## 2. 교사 API

### 완료
- POST /api/teacher/login
- GET /api/teacher/me/students?year=2026
- POST /api/teacher/check

### 레거시 테스트용 (제거 예정)
- GET /api/teacher/{teacherId}/students?year=2026
- POST /api/teacher/{teacherId}/check

### 동작 설명
- 로그인 성공 시 accessToken 반환
- JWT Bearer 토큰으로 /me/students, /check 호출 가능
- 교사는 자기 반 학생만 조회 가능
- 교사는 자기 반 학생만 체크 가능
- 체크는 insert / update / delete(둘 다 false) 방식

---

## 3. 관리자 API

### 완료
- POST /api/admin/years
- PATCH /api/admin/years/{yearId}
- POST /api/admin/year-classes
- POST /api/admin/year-classes/{yearClassId}/teachers
- POST /api/admin/year-classes/{yearClassId}/students
- DELETE /api/admin/year-classes/{yearClassId}/teachers
- DELETE /api/admin/year-classes/{yearClassId}/students
- PATCH /api/admin/year-classes/{targetYearClassId}/students/move
- PATCH /api/admin/year-classes/{yearClassId}
- GET /api/admin/me
- GET /api/admin/bootstrap
- GET /api/admin/years
- GET /api/admin/year-classes?year=2026
- GET /api/admin/year-classes/{yearClassId}
- GET /api/admin/audit-logs?limit=100&offset=0
- GET /api/admin/audit-logs.csv?limit=1000&offset=0
- GET /api/admin/audit-logs/action-types
- GET /api/admin/teachers?activeOnly=true&keyword=&limit=20&offset=0
- GET /api/admin/students?activeOnly=true&keyword=&limit=20&offset=0

### 미완료
- 권한/운영 정책 기반 관리자 웹 UI 추가 개선(정식 화면 전환)

### 동작 설명
- 관리자 API는 JWT로 teacherId 추출 후 ADMIN 권한 검증 필요
- 연도/반/배정 관리자 콘솔 화면(`/admin.html`)에서 로그인 후 주요 API 호출 가능
- `GET /api/admin/bootstrap`은 `me`, `years`, `auditLogActionTypes`, `yearClasses`를 함께 반환
- `GET /api/admin/bootstrap`은 `schemaVersion`(`v1`)을 함께 반환
- `GET /api/admin/bootstrap`은 `generatedAt`(UTC ISO-8601) 타임스탬프를 함께 반환
- `GET /api/admin/bootstrap`은 `selectedYear`(서버가 실제 선택한 기준 연도)를 함께 반환
- `GET /api/admin/bootstrap`은 `teachers`/`students` 첫 페이지(activeOnly=true, limit=20, offset=0)도 함께 반환
- `GET /api/admin/bootstrap`은 정규화된 초기 필터 상태 `pool`, `audit` 메타를 함께 반환
- `bootstrap.audit` 메타는 `actorTeacherId`를 포함해 적용된 로그 필터를 그대로 반환
- `GET /api/admin/bootstrap?includePools=false&includeAuditLogs=false`로 초기 응답 섹션을 선택적으로 제외 가능
- `GET /api/admin/bootstrap?includeYearClasses=false&includeActionTypes=false`로 반목록/actionType 섹션도 선택적으로 제외 가능
- `GET /api/admin/bootstrap?year=2026`으로 특정 연도 기준 `yearClasses` 초기 로딩 가능
- `GET /api/admin/bootstrap?poolKeyword=kim&poolActiveOnly=true&poolLimit=20`으로 초기 풀 필터를 함께 적용 가능
- `GET /api/admin/bootstrap?auditLimit=100&auditOffset=0&auditActorTeacherId=12&auditActionType=MOVE_STUDENT&auditKeyword=abc&auditFromAt=2026-03-01T00:00&auditToAt=2026-03-31T23:59`로 초기 로그 필터를 함께 적용 가능
- `GET /api/admin/bootstrap`에서 `auditActorTeacherId` 필터도 지원
- 존재하지 않는 `year` 파라미터로 호출 시 400 + `연도가 존재하지 않습니다.` 반환
- 관리자 콘솔에서 반/교사/학생 필터, 배정 해제, 학생 반 이동 지원
- 관리자 콘솔에서 audit_logs 조회(페이지/필터) 지원
- 관리자 콘솔에서 반 수정, 교사/학생 풀 조회, audit_logs CSV 다운로드 지원
- 관리자 콘솔에서 반 카드 인라인 편집/해제/이동, 풀 페이징/다중선택 배정 지원
- 교사/학생 풀 조회 API는 totalCount/limit/offset/items 기반 서버사이드 페이징 지원
- 교사/학생 다중 선택 상태(페이지 전환 간) 및 로그/풀 검색 조건 로컬 저장 지원
- 배정/해제/이동 작업은 confirm + 마지막 작업 undo 지원
- `app.admin-console.enabled=false` 시 관리자 콘솔 정적 리소스 비노출(404)
- 정식 관리자 UI 라우트 `/admin` 추가(내부적으로 `/admin.html` 포워드)
- 분리 프론트 배포용 CORS(`app.admin-frontend.allowed-origins`) 적용
- 서비스/컨트롤러 통합 테스트(H2)로 권한/중복/이동/JWT 시나리오 검증 완료
- 컨트롤러 통합 테스트로 audit-logs 필터/CSV/action-types, 교사 페이징 시나리오 검증 완료
- 컨트롤러 통합 테스트로 undo 연동 시나리오(배정-해제-재배정, 이동-원복) 검증 완료
- 컨트롤러 통합 테스트로 admin-console enabled/disabled 접근 제어 시나리오 검증 완료
- 컨트롤러 통합 테스트로 CORS preflight 허용/비허용 Origin 시나리오 검증 완료
- audit_logs 컬럼 표준화(action_type/detail/created_at) 로직 반영(유사 컬럼 자동 매핑)
- CI(`backend-ci`) + 수동 스테이징 스모크(`staging-smoke`) 워크플로 구성 완료

---

## 4. 공통 규칙

- 예외는 IllegalArgumentException 중심
- GlobalExceptionHandler 사용
- DTO는 record 사용
- 서비스 로직은 Service 계층에 유지
- 기존 패키지 구조 유지
