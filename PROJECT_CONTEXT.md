# PROJECT_CONTEXT.md

## 1. 프로젝트 개요

교회 초등부 주일학생 큐티(QT) 및 말씀노트 작성 여부를 관리하는 시스템.

구성:

- 학생: 자기 달력만 조회 (체크 불가)
- 교사: 자기 반 학생 조회 및 체크 가능
- 관리자(전도사): 연도/반 생성 및 교사·학생 배정

백엔드:

- Spring Boot (Gradle)
- JPA (Hibernate)
- MySQL
- JWT 인증
- BCrypt 비밀번호

---

## 2. 현재 완료된 기능

### 학생
- 최신 공개 연도 기준 학생 목록 조회
- 월별 달력 조회
- qtChecked / noteChecked 표시
- 오늘 날짜 표시
- 연도 누적 개수 계산

### 교사
- 로그인 (BCrypt 검증)
- JWT accessToken 발급
- 자기 반 학생 목록 조회
- 체크 저장 / 수정 / 삭제
- 권한 검증 (자기 반만 가능)

### 관리자
- 연도 생성
- 연도 공개 여부 수정
- 반 생성

---

## 3. 아직 미구현 기능

### 관리자
- 반에 교사 배정 API
- 반에 학생 배정 API
- 연도 목록 조회
- 반 목록 조회
- 반별 교사/학생 조회

### 기타
- 관리자 웹 UI (추후 Spring MVC or React 고려)

---

## 4. 도메인 규칙

- 학생은 보기만 가능 (체크 불가)
- 체크는 교사만 가능
- 체크 항목: qtChecked, noteChecked (2개만)
- 학생 목록은 최신 공개 연도 기준
- 학생은 한 연도에 하나의 반만 소속 가능
- 한 반에는 교사 여러 명 가능
- 학년은 student.schoolGrade 숫자로 저장 (표시는 n학년)
- 반은 학년 개념 없음

---

## 5. 주요 테이블

- years
- students
- teachers
- year_classes
- year_class_teachers
- year_class_students
- devotion_checks
- audit_logs

---

## 6. 패키지 구조

com.church.qt

- studentapp
- teacherapp
- admin
- domain.*
- common

---

## 7. 구현 스타일 규칙 (중요)

작업 시 반드시 아래 규칙 유지:

- 기존 패키지 구조 유지 (절대 변경 금지)
- 기존 naming 스타일 유지
- DTO는 record 사용
- 서비스 구조 유지
- 예외 처리: IllegalArgumentException + GlobalExceptionHandler
- 관리자 API는 JWT에서 teacherId 추출 후 ADMIN 검증
- 불필요한 리팩토링 금지
- 새 구조 임의 도입 금지

---

## 8. 현재 API 상태

### 완료

POST /api/teacher/login  
GET /api/teacher/me/students  
POST /api/teacher/check

POST /api/admin/years  
PATCH /api/admin/years/{yearId}  
POST /api/admin/year-classes

### 미완료

POST /api/admin/year-classes/{yearClassId}/teachers  
POST /api/admin/year-classes/{yearClassId}/students

---

## 9. 다음 작업 (우선순위)

1. 반에 교사 배정 API 구현
2. 반에 학생 배정 API 구현
3. 관리자 조회 API 구현

---

## 10. Codex 작업 지침

Codex는 항상 다음 규칙으로 작업해야 한다:

1. PROJECT_CONTEXT.md 먼저 읽을 것
2. 현재 코드 구조 분석 후 작업할 것
3. 기존 구조 유지할 것
4. 필요한 변경 파일 목록 먼저 제시할 것
5. 이후 구현 코드 제시할 것

절대:

- 새 패키지 만들지 말 것
- 구조 바꾸지 말 것
- 대규모 리팩토링 하지 말 것