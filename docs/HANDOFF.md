# 인수인계 문서 (HANDOFF)

> KT DS 「클린코드 & 리팩토링 실전」 관통 실습 — legacy-portal
> 작성 시점 기준 `main` 브랜치 최신 커밋: **`93782a8`** (God Class 해체 완료)
> 이 문서 하나로 지금까지의 맥락·규칙·다음 할 일을 이어받을 수 있게 정리했다.

---

## 0. 이 프로젝트가 뭐냐

사내 업무 포털(결재/공지/일정)을 **의도적으로 안티패턴 범벅으로 짜놓은 레거시**에서 출발해,
**겉보기 동작을 100% 보존한 채 내부 구조만 단계별로 리팩토링**하는 학습 프로젝트.

- Java 21 · Spring Boot 3.2 · JPA · JUnit 5
- DB: MariaDB 있으면 사용, 없으면 자동 H2 인메모리(MODE=LEGACY)
- 실행: `.\mvnw.cmd spring-boot:run` (포트 8080) / 테스트: `.\mvnw.cmd test`
- 원격: github.com/BulNim/legacy-portal.git · **커밋하면 항상 origin에 자동 push**

### 도메인/패키지
- `approval`(결재) · `notice`(공지) · `schedule`(일정) · `user`(사용자) · `common`(메일·로그)
- **`approval` 패키지만 계층 하위분리 완료**: `controller` / `service` / `domain` / `repository` / `dto`
  (notice·schedule은 아직 레거시 평면 구조 — 리팩토링 미착수)

---

## 1. 절대 어기면 안 되는 규칙 (이게 이 프로젝트의 핵심)

리팩토링 = **내부 구조만 개선, 외부에서 관찰 가능한 것은 절대 안 바꿈**.

| 항목 | 규칙 |
|---|---|
| API 엔드포인트 | URL·HTTP 메서드 유지 (`/api/approvals/{id}/process` 를 submit/approve로 쪼개지 않음) |
| public 메서드 계약 | 시그니처·이름 유지 (`processApproval(id, userId, action, reason)` 그대로, 내부만 위임) |
| 요청/응답 형식 | 기존 JSON 구조·필드명 그대로 |
| **DB 저장값** | enum을 써도 DB엔 **정수 그대로**(0/1/2/3/9). `@Convert`+`AttributeConverter`로 매핑. **`@Enumerated(STRING/ORDINAL) 금지`** (STRING은 값이 바뀌고 ORDINAL은 9와 순번이 어긋남) |
| 관찰 동작 | 조용히 무시하던 케이스를 예외로 바꾸지 않음. 상태 전이 결과·에러 동작을 레거시와 동일하게 |

### ⚠️ 특성화 테스트 절대 불변 규칙 (제일 중요 — 과거에 여기서 사고 냄)
- **`ApprovalServiceCharacterizationTest.java`는 리팩토링 중 절대 수정 금지.** 테스트 코드 한 줄도 못 바꾼다.
- enum을 도입해도 테스트는 **정수 리터럴(0/1/2/3/9)로 그대로 호출·검증**한다.
- **과거 실수**: enum 전환 때 `getStatus()` 반환타입이 바뀐 줄 알고 `.isEqualTo(0)` → `.isEqualTo(DRAFT)`로 고쳤다가
  사용자가 강하게 지적함. → **해결책이 이 프로젝트의 핵심 패턴**: 내부 필드만 enum, **getter/setter는 int 시그니처 유지**
  (`getStatus()`가 `status.code()` 반환). 그래서 테스트는 손 안 대도 green.
- **예외**: `@Import`에 빈(bean) 추가 같은 **DI 배선 변경은 테스트 위배 아님**(사용자 확인함). 테스트 "로직"만 불변.
- **특성화 테스트는 사용자가 "진행해"라고 명시할 때만 실행**한다. 리팩토링 후 자동 실행 금지(메모리에 저장됨).

### 보존 vs 개선 구분
- 레거시의 나쁜 설계(예: 권한 없는 승인을 **조용히 무시**)도 **리팩토링 단계에선 그대로 보존**한다.
- 결함을 지금 고치면 리팩토링이 아니라 동작 변경 → **리팩토링 완료 후 별도 재설계 단계**에서 다룬다.

### 코딩/네이밍
- 약어·한 글자 금지(지역변수·파라미터·반환값 예외 없음). `d→approval, u→actor, s→status, proc 제거→action 직접`
- boolean = `is`/`has` 접두
- enum 타입명은 소속 도메인을 드러내게 (Priority/Role보다 ApprovalPriority 등 선호 — 아직 미적용, 아래 백로그 참고)
- **리팩토링한 코드엔 반드시 상세 주석**: 클래스 상단 `[리팩토링] 레거시 X → Y (기법·이유)`, 변경 지점 인라인 주석. (학습 산출물이라 필수)

---

## 2. 지금까지 한 일 (커밋 순)

| 커밋 | 내용 |
|---|---|
| `refactor/start` (태그) | 리팩토링 시작 기준점 |
| `ecb0b55` | **특성화 테스트 6개** 추가(안전망), 상태전이 규칙·커버리지 문서화 |
| `82464d9` | **매직넘버 → enum**(status/type/priority/role + AttributeConverter) + processApproval 분해(guard clause + switch) (BL-02·BL-09) |
| `e56c90a` | REPORT.md (refactor/start..HEAD 변경 요약) |
| `25a966e` | approval 패키지 **계층별 하위 패키지 분리** (controller/service/domain/repository) |
| `6df58ab` | 요청/응답 **record DTO 분리** (CreateApprovalRequest/ProcessRequest/ApprovalResponse) (BL-04) |
| `ee8e3a1` | Swagger(springdoc-openapi **2.3.0** — Boot 3.2 호환) + API 테스트 보고서 |
| `93782a8` | **God Class 해체** — Move Method + Extract Method + DI + Rich Domain 상태전이 (← 최신) |

### 최신 커밋(93782a8)에서 한 것 상세
1. **Move Method**: `statusLabel`/`amountGrade`를 서비스 → `Approval` 도메인으로. 금액 등급은 `AmountGrade` enum 신설.
2. **Extract Method**: 메일 본문 조립 → `submittedBody`/`approvedBody`/`rejectedBody` (서비스 private 유지).
3. **DI (BL-03)**: `new SmtpMailSender()`/`new FileAuditLogger()` → `MailSender`/`AuditLogger` 인터페이스 생성자 주입.
   구현체는 `@Component`.
4. **Rich Domain**: 상태전이·권한 규칙을 `Approval.submit/approve/reject/cancel`로 이동.
   **위반 시 예외 아니라 `return false`**(레거시 "조용히 무시" 보존) → 서비스는 true일 때만 저장·메일·감사로그.
   `User.isManagerOrAbove()` 추가(role>=2 캡슐화). `ApprovalService`는 오케스트레이션만 담당.

---

## 3. 현재 코드 상태 (핵심 파일 스냅샷)

### `approval/service/ApprovalService.java` — 오케스트레이션만
- 생성자 DI: `(ApprovalRepository, UserRepository, MailSender, AuditLogger)`
- `processApproval`: null guard 2개 → `ApprovalAction.fromCode(action)` null guard → `switch`로 submit/approve/reject/cancel 위임
- private: `submit/approve/reject/cancel`(각각 `approval.xxx()` 성공 시에만 save+mail+audit), 메일 본문 3개, `writeAudit`
- **아직 남은 것**: `create()` 안의 감사로그 6줄 복붙(writeAudit로 통일 안 됨), 메일 본문이 여전히 서비스 private

### `approval/domain/Approval.java` — Rich Domain
- enum 필드(`ApprovalType`/`ApprovalStatus`/`Priority`) + `@Convert`, **getter/setter는 int 시그니처**(`code()`/`fromCode()`)
- `statusLabel()`(→ status.label()), `amountGrade()`(→ AmountGrade.of(amount).name())
- `submit/approve/reject/cancel(actor[,reason])` boolean 반환, `isApprover/isDrafter/touch` private
- import `com.ktds.portal.user.User`

### `approval/domain/AmountGrade.java` (신설)
- `S(1000만)/A(100만)/B(10만)/C(0)`, `threshold()`, `of(amount)`. 반환 문자열 "S/A/B/C" 레거시 동일.

### `user/User.java`
- role = `Role` enum + `@Convert(RoleConverter)`, **int getRole/setRole**, 생성자 int 받음, `isManagerOrAbove()` 추가

### enum/converter (approval/domain)
- ApprovalStatus(0/1/2/3/**9**) · ApprovalType(1~4) · Priority(1~3) · **ApprovalAction**(1/2/3/9, 저장 필드 아니라 converter 없음, 파라미터 전용) · Role(1~3)
- Converter: ApprovalStatusConverter · ApprovalTypeConverter · PriorityConverter · RoleConverter
- 각 enum: `code()` / `label()`(있는 것) / `fromCode()`. **`@JsonValue` 안 씀**(int getter가 JSON 직렬화 담당)

### `common/`
- `MailSender`(인터페이스) ← `SmtpMailSender`(@Component) / `AuditLogger`(인터페이스) ← `FileAuditLogger`(@Component). 콘솔 출력 보존.

### `approval/dto/` (record)
- `CreateApprovalRequest`(title,content,type,priority,drafterId,approverId,amount,urgent)
- `ProcessRequest`(userId,action,reason + `reasonOrEmpty()`)
- `ApprovalResponse`(엔티티 필드 12개 순서대로, `from()`/`fromList()` 정적 팩토리)

### 특성화 테스트 `src/test/.../approval/ApprovalServiceCharacterizationTest.java`
- `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` + `@Import({ApprovalService, SmtpMailSender, FileAuditLogger})`
- 6개(한글 시나리오명): 상신→승인 / 반려 / 취소 / 권한없는승인(무시) / 없는id(무시) / 재승인(무시·updatedAt 불변)
- 단정은 전부 **int 리터럴(0/1/2/3/9)**. **최근 실행 6/6 green**.

---

## 4. 다음 할 일 (백로그 — docs/4-12 참조)

리팩토링은 **스텝 바이 스텝**으로 진행한다. 사용자가 다음 스텝을 지시하면 그때 한 단계씩.
(사용자 지침: "강의 중 실습이라 스텝바이스텝. 매번 같은 확인 질문 하지 말 것.")

우선순위 후보(아직 안 한 것):
- [ ] `ApprovalService.create()`의 감사로그 복붙 6줄을 `writeAudit`로 통일 (중복 제거 마무리)
- [ ] 메일 본문을 서비스 밖(도메인/전용 포매터)으로 뺄지 검토 — 현재는 서비스 private 유지 중
- [ ] enum 타입명 도메인화: `Priority→ApprovalPriority`, `Role`은 user 도메인이라 유지 검토 (CLAUDE.md 네이밍 규칙, 사용자와 상의 필요)
- [ ] `notice`·`schedule` 패키지 리팩토링 (아직 레거시 평면 구조, 매직넘버·중복 존재)
- [ ] 3개 서비스 중복 코드 정리(docs/4-5) — 공통 메일/감사로그 패턴
- [ ] **재설계 단계**(리팩토링 완료 후): 권한 없는 승인을 조용히 무시 → 예외로 전환 등 **동작 변경**은 여기서

---

## 5. 환경/도구 함정 (반복해서 겪은 것)

- **Windows + Git Bash 세션**: `.bat`/`mvnw.cmd` 실행은 **PowerShell 도구**로. `cmd /c`는 이 세션에서 빈 콘솔만 뜨고 실행 안 됨.
- **curl 한글 → 400**: Git Bash가 한글을 CP949로 보내 UTF-8 400 발생. API 테스트는 **Playwright 브라우저 `page.evaluate` fetch**로.
- **Swagger UI React 입력**: `input` 이벤트 주입은 안 먹힘 → Playwright `fill`(clear+type) 사용.
- **springdoc 버전**: Boot 3.2엔 **2.3.0**. (3.0.3/2.8.x는 Boot 3.4+ 필요)
- **push 거부(non-fast-forward)**: 원격에 없는 커밋 있으면 `git rebase origin/main` 후 push.

## 6. SonarQube (프로젝트 밖)
- 위치: `D:\ktds\sonarqube` (Docker 없이, Java 21). 한국어 언어팩 `sonar-l10n-ko` 설치됨.
- 서버: `StartSonar` → localhost:9000. admin 계정 정보는 sonarqube 폴더 내 저장해둠.
- 분석 보고서(docx)는 `docs/`에 있음.

## 7. 참고 문서 (docs/)
- 진단: `4-4`(구조) `4-5`(중복) `4-7`(긴 메서드) `4-8`(매직넘버) `4-9`(강결합) `4-10`(심각도표) `4-11`(스코어링) `smells.md`
- 계획: `4-12. 리팩토링 백로그.md` · `4-13. processApproval 상태 전이 규칙.md`
- 결과: `REPORT.md` · `리팩토링 효과 분석.md` · `특성화 테스트 결과.md` · `enum · 약어 rename · Replace Temp 리팩토링.md`
- docx: SonarQube 분석 / Swagger API 테스트 / Swagger 상위직급자 테스트 / 사용자 테스트 보고서
