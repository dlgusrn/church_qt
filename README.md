# 📘 Church QT Admin / App

교회 QT 관리 시스템 (운영 콘솔 + 실제 사용 앱 UI)

현재 프로젝트는 아래 두 가지 레이어로 구성되어 있습니다.

- `/admin`, `/ops` → 운영/검증용 콘솔  
- `/app/...` → 실제 사용자(학생/교사)용 모바일 우선 UI  

---

# 🧩 기술 스택

- Backend: Spring Boot (Java)  
- DB: MySQL  
- Frontend(UI): Vanilla JS + HTML + CSS  
- 인증: JWT (교사용)

---

# 📂 주요 구조

```
src/main/java
 └─ controller
     ├─ AdminController
     ├─ TeacherController
     └─ AppUiController   ← /app 라우팅

src/main/resources/static
 ├─ admin/
 ├─ ops/
 └─ app/
     ├─ app.html
     ├─ app.css
     └─ app.js
```

---

# 🚀 실행 방법

## 1. 서버 실행

```bash
./gradlew bootRun
```

또는

```bash
./gradlew build
java -jar build/libs/*.jar
```

---

## 2. 접속 경로

### 운영 콘솔 (기존 유지)

Admin Console  
```
http://localhost:8080/admin
```

Ops Console  
```
http://localhost:8080/ops
```

---

### 실제 앱 화면

## 학생용

학생 선택:

```
http://localhost:8080/app/student
```

학생 달력:

```
/app/student/calendar/{studentId}?year=YYYY&month=MM
```

---

## 교사용

로그인:

```
http://localhost:8080/app/teacher/login
```

학생 목록:

```
/app/teacher/students?year=YYYY
```

학생 체크:

```
/app/teacher/students/{studentId}/calendar?year=YYYY&month=MM
```

---

# 🔐 인증 흐름 (교사용)

1. 로그인:

```http
POST /api/teacher/login
```

응답:

```json
{
  "accessToken": "..."
}
```

2. 토큰 저장:

```js
localStorage.setItem("qt_teacher_access_token", token)
```

3. 이후 요청:

```http
Authorization: Bearer {token}
```

---

# 📡 주요 API

## 학생

학생 목록:

```http
GET /api/students
```

학생 달력:

```http
GET /api/students/{studentId}/calendar?year=YYYY&month=MM
```

---

## 교사

로그인:

```http
POST /api/teacher/login
```

학생 목록:

```http
GET /api/teacher/me/students?year=YYYY
```

체크 저장:

```http
POST /api/teacher/check
```

예시:

```json
{
  "studentId": 1,
  "year": 2026,
  "date": "2026-03-09",
  "qtChecked": true,
  "noteChecked": false
}
```

※ 둘 다 false면 백엔드에서 삭제 처리됨

---

# 📱 UI 구성

## 학생용

### 1. 학생 선택
- 카드형 목록
- 이름 클릭 → 달력 이동

### 2. 달력
- QT/노트 요약 표시
- 🍇 QT / 🫒 노트
- 월 이동 지원

---

## 교사용

### 1. 로그인
- JWT 발급

### 2. 학생 목록
- QT/노트/총 개수 표시

### 3. 체크 화면
- 날짜 선택
- QT/노트 토글
- 저장 후 즉시 반영

---

# ⚠️ 주의사항

## 1. 연/월 파라미터

학생 달력 API는 반드시:

```
year=YYYY&month=MM
```

전달해야 함.

프론트는 잘못된 값이면 현재 연/월로 자동 대체함.

---

## 2. 토큰 만료

토큰 없거나 만료 시:

```
/app/teacher/login
```

으로 자동 이동

---

# 📌 향후 계획

- PWA 적용 (설치형 웹앱)
- 오프라인 캐싱
- UI 애니메이션 개선
- 관리자 통계 페이지 확장

---

# 👨‍💻 메모

현재 `/admin`, `/ops`는 개발/검증용으로 유지 중이며  
실제 서비스 UI는 `/app/...` 기준으로 확장 예정.
