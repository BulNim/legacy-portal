package com.ktds.portal.approval.dto;

import com.ktds.portal.approval.domain.Approval;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [리팩토링] API 응답에서 엔티티(Approval) 직접 노출 → record DTO로 분리 (docs/4-12 BL-04).
 * [보존 대상] JSON 필드명·순서·타입은 레거시(Approval 엔티티 직렬화)와 100% 동일 —
 *  id/title/content/type/status/priority/drafterId/approverId/rejectReason/amount/createdAt/updatedAt.
 *  type/status/priority는 정수(엔티티의 int getter 값 그대로). static/index.html이 a.status===1 처럼
 *  정수로 비교하므로 반드시 정수 유지.
 */
public record ApprovalResponse(
        Long id,
        String title,
        String content,
        int type,
        int status,
        int priority,
        Long drafterId,
        Long approverId,
        String rejectReason,
        long amount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ApprovalResponse from(Approval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getTitle(),
                approval.getContent(),
                approval.getType(),
                approval.getStatus(),
                approval.getPriority(),
                approval.getDrafterId(),
                approval.getApproverId(),
                approval.getRejectReason(),
                approval.getAmount(),
                approval.getCreatedAt(),
                approval.getUpdatedAt()
        );
    }

    public static List<ApprovalResponse> fromList(List<Approval> approvals) {
        return approvals.stream().map(ApprovalResponse::from).toList();
    }
}
