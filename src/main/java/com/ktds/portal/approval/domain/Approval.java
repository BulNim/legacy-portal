package com.ktds.portal.approval.domain;

import com.ktds.portal.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 결재 엔티티.
 *
 * [스멜] 캡슐화 부재 — 모든 필드에 public setter. 누구나 상태를 마음대로 바꿀 수 있다.
 *
 * [리팩토링] type/status/priority 필드를 enum으로 전환 + AttributeConverter로 DB엔 정수 그대로 저장
 * (docs/4-12 BL-02). @Enumerated(STRING/ORDINAL) 금지 — STRING은 저장값이 바뀌고 ORDINAL은 9와 순번이 어긋난다.
 * [보존 대상] getter/setter는 레거시처럼 int 시그니처를 유지한다(public 계약·JSON·특성화 테스트 불변).
 * 내부 필드만 enum이고, 경계(getter/setter)에서 code()/fromCode()로 int와 변환한다.
 *
 * [리팩토링] 상태 전이·권한 규칙을 서비스 if-지옥에서 이 도메인으로 이동(Rich Domain, BL-09 연장).
 *  - submit()/approve(actor)/reject(actor,reason)/cancel(actor)이 상태가드+권한판정을 스스로 수행.
 *  - [보존 대상] 위반 시 예외가 아니라 boolean false 반환(레거시 "조용히 무시" 동작 유지) → 서비스는
 *    true일 때만 저장·메일·감사로그 등 부수효과를 실행한다.
 */
@Entity
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;
    @Convert(converter = ApprovalTypeConverter.class)
    private ApprovalType type;         // DB: 1=지출 2=휴가 3=구매 4=기타
    @Convert(converter = ApprovalStatusConverter.class)
    private ApprovalStatus status;     // DB: 0=임시저장 1=상신 2=승인 3=반려 9=취소
    @Convert(converter = PriorityConverter.class)
    private Priority priority;         // DB: 1=낮음 2=보통 3=높음
    private Long drafterId;     // 기안자
    private Long approverId;    // 결재자
    private String rejectReason;
    private long amount;        // 지출/구매 금액
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    // [보존 대상] getter/setter는 int 시그니처 유지 — 내부 enum과 code()/fromCode()로 변환한다.
    public int getType() { return type.code(); }
    public void setType(int type) { this.type = ApprovalType.fromCode(type); }
    public int getStatus() { return status.code(); }
    public void setStatus(int status) { this.status = ApprovalStatus.fromCode(status); }
    public int getPriority() { return priority.code(); }
    public void setPriority(int priority) { this.priority = Priority.fromCode(priority); }
    public Long getDrafterId() { return drafterId; }
    public void setDrafterId(Long drafterId) { this.drafterId = drafterId; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // [리팩토링] ApprovalService.statusLabel()을 도메인으로 Move Method (표현 규칙은 상태 자신이 안다).
    //           내부 status 필드는 enum이므로 라벨을 스스로 가진 ApprovalStatus.label()에 위임한다.
    public String statusLabel() {
        return status.label();
    }

    // [리팩토링] ApprovalService.amountGrade()를 도메인으로 Move Method + 등급 규칙은 AmountGrade enum에 위임.
    //           [보존 대상] 반환값("S"/"A"/"B"/"C")은 레거시와 동일.
    public String amountGrade() {
        return AmountGrade.of(amount).name();
    }

    // ===== 상태 전이 규칙 (서비스 if-지옥에서 이동) =====================================
    // [보존 대상] 판정 순서·조건은 레거시와 동일. 위반 시 false 반환(예외 아님) → 서비스가 부수효과를 건너뛴다.

    /** [상신] 임시저장(DRAFT)일 때만 상신 가능. 성공 시 고액 지출 우선순위 자동 상향까지 수행. */
    public boolean submit() {
        if (status != ApprovalStatus.DRAFT) {
            return false;
        }
        // [도메인 규칙] 지출 && 100만원 이상이면 우선순위 자동 상향(레거시와 동일).
        if (type == ApprovalType.EXPENSE && amount >= AmountGrade.A.threshold()) {
            priority = Priority.HIGH;
        }
        status = ApprovalStatus.SUBMITTED;
        touch();
        return true;
    }

    /** [승인] 상신 상태 + 결재자 본인 + 팀장 이상일 때만. */
    public boolean approve(User actor) {
        if (status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (!isApprover(actor.getId()) || !actor.isManagerOrAbove()) {
            return false;
        }
        status = ApprovalStatus.APPROVED;
        touch();
        return true;
    }

    /** [반려] 승인과 동일 권한 판정. 성공 시 반려 사유 저장. */
    public boolean reject(User actor, String reason) {
        if (status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (!isApprover(actor.getId()) || !actor.isManagerOrAbove()) {
            return false;
        }
        status = ApprovalStatus.REJECTED;
        rejectReason = reason;
        touch();
        return true;
    }

    /** [취소] 아직 승인 전(DRAFT/SUBMITTED) + 기안자 본인일 때만. */
    public boolean cancel(User actor) {
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (!isDrafter(actor.getId())) {
            return false;
        }
        status = ApprovalStatus.CANCELED;
        touch();
        return true;
    }

    private boolean isApprover(Long userId) {
        return approverId != null && approverId.equals(userId);
    }

    private boolean isDrafter(Long userId) {
        return drafterId != null && drafterId.equals(userId);
    }

    private void touch() {
        updatedAt = LocalDateTime.now();
    }
}
