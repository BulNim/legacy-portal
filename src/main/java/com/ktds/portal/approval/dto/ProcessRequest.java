package com.ktds.portal.approval.dto;

/**
 * [리팩토링] POST /api/approvals/{id}/process 요청 바디를 Map<String,Object> → record DTO로 (docs/4-12 BL-04).
 * [보존 대상] JSON 필드명은 레거시 Map 키와 동일(userId/action/reason).
 *  - action: 1=상신 2=승인 3=반려 9=취소 (정수 그대로 — API 계약 유지, 매직넘버 노출은 재설계 단계 몫)
 *  - reason 생략 시 null → 컨트롤러에서 "" 로 보정(레거시 getOrDefault("reason","") 동작 보존).
 */
public record ProcessRequest(
        Long userId,
        int action,
        String reason
) {
    /** [보존 대상] reason 미전송 시 빈 문자열(레거시와 동일). */
    public String reasonOrEmpty() {
        return reason == null ? "" : reason;
    }
}
