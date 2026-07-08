package com.ktds.portal.approval;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 결재 엔티티.
 *
 * [스멜] 빈약한 도메인 모델(Anemic Domain Model) — 데이터만 있고 행위가 없다.
 * [스멜] 캡슐화 부재 — 모든 필드에 public setter. 누구나 상태를 마음대로 바꿀 수 있다.
 *
 * [리팩토링] type/status/priority 필드를 enum으로 전환 + AttributeConverter로 DB엔 정수 그대로 저장
 * (docs/4-12 BL-02). @Enumerated(STRING/ORDINAL) 금지 — STRING은 저장값이 바뀌고 ORDINAL은 9와 순번이 어긋난다.
 * [보존 대상] getter/setter는 레거시처럼 int 시그니처를 유지한다(public 계약·JSON·특성화 테스트 불변).
 * 내부 필드만 enum이고, 경계(getter/setter)에서 code()/fromCode()로 int와 변환한다.
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
}
