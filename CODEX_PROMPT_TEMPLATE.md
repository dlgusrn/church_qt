# CODEX_PROMPT_TEMPLATE.md

항상 먼저 아래 파일들을 읽고 시작할 것:

1. PROJECT_CONTEXT.md
2. API_STATUS.md
3. NEXT_TASK.md

그 후 아래 규칙을 반드시 지킬 것:

- 현재 프로젝트 구조와 패키지 경로를 절대 바꾸지 말 것
- 기존 naming, DTO(record), Service 스타일 유지
- 예외 처리 방식(GlobalExceptionHandler + IllegalArgumentException) 유지
- 관리자 API는 JWT에서 teacherId 추출 후 ADMIN 검증 유지
- 새 구조를 임의로 도입하지 말 것
- 대규모 리팩토링 하지 말 것
- 필요한 파일만 수정할 것

작업 응답 형식:

1. 먼저 수정/추가가 필요한 파일 목록 제시
2. 각 파일에서 무엇을 바꾸는지 요약
3. 이후 실제 코드 제안
4. 마지막에 Postman 테스트 예시 제시

주의:
- 현재 프로젝트는 이미 동작 중인 코드가 있으므로, 기존 API를 깨지 않게 작업할 것
- 테스트용 레거시 API는 제거하지 말고 유지할 것 (별도 지시 전까지)