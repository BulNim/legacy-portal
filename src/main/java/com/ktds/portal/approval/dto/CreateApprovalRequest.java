package com.ktds.portal.approval.dto;

/**
 * [리팩토링] POST /api/approvals 요청 바디를 Map<String,Object> → record DTO로 (docs/4-12 BL-04).
 * [보존 대상] JSON 필드명은 레거시 Map 키와 100% 동일(title/content/type/priority/drafterId/approverId/amount/urgent).
 *  - type: 1=지출 2=휴가 3=구매 4=기타,  priority: 1=낮음 2=보통 3=높음 (정수 그대로 — API 계약 유지)
 *  - amount 생략 시 0, urgent 생략 시 false (primitive 기본값 = 레거시 getOrDefault 동작과 동일)
 */
public record CreateApprovalRequest(
        String title,
        String content,
        int type,
        int priority,
        Long drafterId,
        Long approverId,
        long amount,
        boolean urgent
) {
}
