# enum · 약어 rename · Replace Temp 리팩토링

`refactor/start` 태그(특성화 테스트 완료 시점) 이후 `ApprovalService`/`Approval`/`User`/`NoticeService`에
적용한 세 가지 리팩토링 기법의 **이전 → 이후** 비교표.

## 대전제 — 무엇을 바꾸지 않았나 (불변)

내부 구조만 바꾸고 **겉보기 계약은 100% 보존**했다(CLAUDE.md 불변 규칙).

| 관찰 대상 | 이전 | 이후 | 변경? |
|---|---|---|---|
| 엔티티 **필드**(내부 표현) | `int` | **`enum` + `@Convert`** | 내부만 바뀜 |
| **getter/setter 시그니처** (`getStatus():int` 등) | `int` | `int` | 그대로 |
| `processApproval(Long,Long,int,String)` 시그니처 | 동일 | 동일 | 그대로 |
| DB 저장값 | 정수 0/1/2/3/9 등 | 정수 0/1/2/3/9 등 (AttributeConverter가 매핑) | 그대로 |
| JSON 응답 (`a.status`, `u.role` …) | 정수 | 정수 (int getter로 직렬화) | 그대로 |
| **특성화 테스트 6개** | — | **한 글자도 안 고침** (`git diff` 0) | 그대로 · 6/6 green |

> **필드 = enum, 경계 = int.** `status`/`type`/`priority`/`role` 필드는 enum이고
> `@Convert`(AttributeConverter)가 DB엔 정수로 저장한다. 하지만 getter/setter는 `int` 시그니처를 유지해
> (`getStatus(){return status.code();}`, `setStatus(int){this.status=fromCode(int);}`) public 계약·JSON·
> 특성화 테스트가 그대로 유지된다. `ApprovalService`는 이 int 경계에서 `fromCode()`로 enum 비교를 한다
> — 기존 `action`을 `ApprovalAction`으로 다루던 방식과 동일.
>
> 예외: **`action`은 저장 필드가 아니라 메서드 파라미터**라 converter가 없다(파라미터 int → 서비스에서 `ApprovalAction.fromCode`만).

---

## 1. enum 상수화 (매직넘버 → enum 상수)

새 enum 5개: `ApprovalStatus` · `ApprovalAction` · `ApprovalType` · `Priority` · `Role`
(각각 `code()` + `fromCode(int)` 보유). 이 중 저장 필드에 해당하는 4개(`ApprovalStatus`/`ApprovalType`/
`Priority`/`Role`)는 전용 **AttributeConverter**(`@Convert`)로 DB엔 정수로 저장한다.
`ApprovalAction`만 converter 없음(파라미터 전용).

| enum | AttributeConverter | 붙는 필드 |
|---|---|---|
| `ApprovalStatus` | `ApprovalStatusConverter` | `Approval.status` |
| `ApprovalType` | `ApprovalTypeConverter` | `Approval.type` |
| `Priority` | `PriorityConverter` | `Approval.priority` |
| `Role` | `RoleConverter` | `User.role` |
| `ApprovalAction` | *(없음 — 메서드 파라미터)* | — |

| 매직넘버 | 의미 | 이전 코드 | 이후 코드 | 위치 |
|---|---|---|---|---|
| `0` | 임시저장 | `d.setStatus(0)` | `approval.setStatus(ApprovalStatus.DRAFT.code())` | create |
| `1` | 상신 | `d.setStatus(1)` | `approval.setStatus(ApprovalStatus.SUBMITTED.code())` | SUBMIT |
| `2` | 승인 | `d.setStatus(2)` | `approval.setStatus(ApprovalStatus.APPROVED.code())` | APPROVE |
| `3` | 반려 | `d.setStatus(3)` | `approval.setStatus(ApprovalStatus.REJECTED.code())` | REJECT |
| `9` | 취소 | `d.setStatus(9)` | `approval.setStatus(ApprovalStatus.CANCELED.code())` | CANCEL |
| `s == 0/1` | 상태 가드 | `int s = d.getStatus(); if (s == 0)` | `ApprovalStatus status = ApprovalStatus.fromCode(approval.getStatus()); if (status == ApprovalStatus.DRAFT)` | processApproval |
| `proc == 1/2/3/9` | 액션 분기 | `if (proc == 1)` | `if (requestedAction == ApprovalAction.SUBMIT)` | processApproval |
| `type == 1` | 지출 | `d.getType() == 1` | `ApprovalType.fromCode(approval.getType()) == ApprovalType.EXPENSE` | SUBMIT |
| `priority = 3` | 높음 | `d.setPriority(3)` / `urgent ? 3 : priority` | `approval.setPriority(Priority.HIGH.code())` / `urgent ? Priority.HIGH.code() : priority` | SUBMIT / create |
| `role >= 2` | 팀장 이상 | `u.getRole() >= 2` | `actor.getRole() >= Role.MANAGER.code()` | APPROVE·REJECT, Notice.publish |

> **범위 밖(그대로 둔 것):** 금액 임계값 `1000000`, `amountGrade()`의 등급 기준(1000만/100만/10만),
> 감사 로그의 `type=` 정수 출력 — 범주형 코드가 아니라 그대로 정수 유지.

---

## 2. 약어 rename (Rename Variable)

의미 없는 한 글자·약어 지역변수를 완전한 이름으로. (CLAUDE.md 네이밍 컨벤션 — 지역변수도 예외 없음)

| 이전 | 이후 | 무엇 | 위치 |
|---|---|---|---|
| `d` | `approval` | 결재 문서(Approval 객체) | create, processApproval |
| `u` | `actor` | 처리 주체(요청한 사용자) | processApproval |
| `s` | `status` | 상태 (동시에 int → ApprovalStatus 지역변수로) | processApproval |
| `proc` | *(제거)* | action을 또 담던 임시변수 → 3장 참조 | processApproval |
| `tmp` | *(제거)* | 라벨 임시변수 → 3장 참조 | statusLabel |

---

## 3. Replace Temp / 임시변수 제거

값을 잠깐 담기만 하던 임시변수와 그에 딸린 분기를 없앴다.

### 3-1. `proc` 임시변수 제거

```java
// 이전 — action을 proc에 복사만 하고 proc로 분기
int proc = action;
if (proc == 1) { ... } else if (proc == 2) { ... }

// 이후 — 파라미터 action을 enum으로 바꿔 직접 분기 (중간 임시변수 없음)
ApprovalAction requestedAction = ApprovalAction.fromCode(action);
if (requestedAction == ApprovalAction.SUBMIT) { ... } else if (requestedAction == ApprovalAction.APPROVE) { ... }
```

### 3-2. `statusLabel()`의 `tmp` + 5분기 if 제거 → enum에 위임

```java
// 이전 — tmp에 담고 5분기 if로 번역
public String statusLabel(Approval d) {
    int s = d.getStatus();
    String tmp;
    if (s == 0) tmp = "임시저장";
    else if (s == 1) tmp = "상신";
    else if (s == 2) tmp = "승인";
    else if (s == 3) tmp = "반려";
    else if (s == 9) tmp = "취소";
    else tmp = "알수없음";
    return tmp;
}

// 이후 — enum이 라벨을 스스로 가지므로 위임 한 줄 (tmp·분기 소멸)
public String statusLabel(Approval approval) {
    return ApprovalStatus.fromCode(approval.getStatus()).label();
}
```

`ApprovalStatus`는 `DRAFT(0,"임시저장")`처럼 code와 label을 함께 갖고, `fromCode()`는 알 수 없는
코드값이면 `null`을 돌려줘 레거시의 `"알수없음"` 방어 위치를 유지한다.

---

## 4. 검증

- `./mvnw.cmd -Dtest=ApprovalServiceCharacterizationTest test` → **Tests run: 6, Failures: 0, Errors: 0** (BUILD SUCCESS)
- 특성화 테스트 파일은 `refactor/start` 커밋과 **byte 단위로 동일**(`git diff HEAD` 결과 없음) — 리팩토링 전후 안전망이 변경 없이 그대로 green임을 확인.
- 변경 파일: `Approval.java`, `ApprovalService.java`, `User.java`, `NoticeService.java` (내부 구조) + enum 5개 신규.
