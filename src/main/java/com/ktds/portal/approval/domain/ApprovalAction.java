package com.ktds.portal.approval.domain;

/**
 * [리팩토링] processApproval()의 action 매직넘버(1/2/3/9) → enum (docs/4-12 BL-02 연장).
 * [보존 대상] processApproval(Long id, Long userId, int action, String reason) 의 public
 * 시그니처는 그대로 유지한다(CLAUDE.md 불변 규칙) — action은 여전히 int로 들어오고,
 * 메서드 내부에서만 이 enum으로 변환해 비교한다. Controller 쪽 계약·요청 바디 형식도 불변.
 */
public enum ApprovalAction {
    SUBMIT(1),
    APPROVE(2),
    REJECT(3),
    CANCEL(9);

    private final int code;

    ApprovalAction(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    // [보존 대상] 정의되지 않은 code(0, 4~8, 10 이상)는 null 반환 — 레거시의 "어느 분기에도 안 걸려
    // 조용히 아무 처리 안 함" 동작과 동일하게, 호출부에서 null과 비교하면 항상 false라 자연히 무시된다.
    public static ApprovalAction fromCode(int code) {
        for (ApprovalAction action : values()) {
            if (action.code == code) {
                return action;
            }
        }
        return null;
    }
}
