# Church QT

교회 QT 관리 시스템입니다. 관리자 웹(`/admin`)과 학생/교사용 앱(`/app/...`)이 함께 들어 있습니다.

## 구성

- 관리자: 대시보드, 연도/반, 교사/학생, 배정, 운영 로그 관리
- 학생 앱: 학생 선택, 월별 달력 조회, 생일자 확인
- 교사 앱: 로그인, 담당 학생 목록, 날짜별 QT/노트/태도 체크

## 기술 스택

- Backend: Spring Boot, JPA, MySQL
- Frontend: Vanilla JS, HTML, CSS
- Auth: JWT, BCrypt

## 주요 URL

- 관리자: `http://localhost:8080/admin`
- 학생 앱: `http://localhost:8080/app/student`
- 교사 로그인: `http://localhost:8080/app/teacher/login`
- 교사 학생 목록: `http://localhost:8080/app/teacher/students?year=2026`

## 실행

```bash
./gradlew bootRun
```

또는

```bash
./gradlew build
java -jar build/libs/*.jar
```

## 현재 핵심 동작

- 학생 목록은 `학생 공개 + 활성` 상태인 최신 연도 기준으로 조회됩니다.
- 학생/교사 달력은 연도와 월을 넘겨 월별 데이터를 조회합니다.
- `years` 테이블에 없는 연도도 학생/교사 달력은 빈 상태로 조회할 수 있습니다.
- 달력 체크 항목은 `QT`, `노트`, `태도` 3개입니다.
- 생일은 날짜 셀의 `🎂`와 하단 생일자 목록으로 표시됩니다.
- 생일자 목록에서 교사는 역할별로 `관리자 / 전도사 / 부장 / 교사`가 표시됩니다.
- 교사/학생 운영 명단은 `year_teachers`, `year_students` 기준으로 관리됩니다.
- 학생 학년은 `students`가 아니라 `year_students.school_grade` 기준입니다.
- 관리자 페이지 접근 가능 역할은 `ADMIN`, `PASTOR`, `DIRECTOR`입니다.
- 관리자 대시보드에서 반별 학생 카드와 학생 달력 바로가기를 제공합니다.
- 배정 화면은 `교사 배정 / 학생 배정` 탭으로 분리되어 있고, 담임/보조 역할 변경을 지원합니다.

## 연도 기반 데이터 구조

- `teachers`, `students`: 사람 마스터
- `year_teachers`, `year_students`: 해당 연도 운영 명단
- `year_classes`: 연도별 반
- `year_class_teachers`, `year_class_students`: 반 배정
- `devotion_checks`: 연도별 체크 기록

## 관리자 화면 구성

- 대시보드: 전체 열매 순위, 반별 학생 카드, 학생 달력 바로가기
- 연도 / 반 관리: 연도 생성/수정, 반 생성/수정
- 교사 / 학생 관리: 연도별 운영 명단 조회, 추가, 수정
- 배정: 연도/반 기준 교사와 학생 배정, 담임/보조 설정
- 운영 로그: 감사 로그 조회와 CSV 다운로드

## 주요 API

### 학생/교사 앱

- `GET /api/students`
- `GET /api/students/current-year`
- `GET /api/students/{studentId}/calendar?year=YYYY&month=MM`
- `POST /api/teacher/login`
- `GET /api/teacher/me/students?year=YYYY`
- `POST /api/teacher/check`

### 관리자

- `GET /api/admin/me`
- `GET /api/admin/bootstrap`
- `GET /api/admin/years`
- `POST /api/admin/years`
- `PATCH /api/admin/years/{yearId}`
- `GET /api/admin/year-classes?year=YYYY`
- `GET /api/admin/year-classes/{yearClassId}`
- `POST /api/admin/year-classes`
- `PATCH /api/admin/year-classes/{yearClassId}`
- `POST /api/admin/year-classes/{yearClassId}/teachers`
- `DELETE /api/admin/year-classes/{yearClassId}/teachers`
- `PATCH /api/admin/year-classes/{yearClassId}/teachers/{teacherId}/assignment-role`
- `POST /api/admin/year-classes/{yearClassId}/students`
- `DELETE /api/admin/year-classes/{yearClassId}/students`
- `GET /api/admin/teachers?year=YYYY&activeOnly=true&limit=10&offset=0`
- `POST /api/admin/teachers?year=YYYY`
- `PATCH /api/admin/teachers/{teacherId}?year=YYYY`
- `GET /api/admin/students?year=YYYY&activeOnly=true&limit=10&offset=0`
- `POST /api/admin/students?year=YYYY`
- `PATCH /api/admin/students/{studentId}?year=YYYY`
- `GET /api/admin/audit-logs?limit=100&offset=0`
- `GET /api/admin/audit-logs.csv?limit=1000&offset=0`
- `GET /api/admin/audit-logs/action-types`

## 참고

- 관리자 정적 파일: `src/main/resources/static/admin.html`, `admin.js`, `admin.css`
- 앱 정적 파일: `src/main/resources/static/app.html`, `app.js`, `app.css`
- 관리자 메인 스타일 조정은 대부분 `admin.css` 모바일 구간(`@media (max-width: 720px)`)에 모여 있습니다.
- 관리자 UI는 연도별 명단 관리와 배정 관리 중심으로 계속 다듬어진 상태라, 세부 동작은 `PROJECT_CONTEXT.md`와 `API_STATUS.md`를 같이 보는 편이 안전합니다.
