package com.ktds.portal.approval;

/**
 * [리팩토링] 우선순위 매직넘버(1/2/3)를 enum 상수로 (docs/4-12 BL-02, ApprovalAction과 동일 방식).
 * [보존 대상] Approval.priority 필드·getPriority()·JSON·DB는 레거시 그대로 int. 이 enum은
 * ApprovalService에서 우선순위 값을 세팅/비교할 때 code()로 변환해 쓰는 용도로만 존재한다.
 */
public enum Priority {
    LOW(1),      // 낮음
    NORMAL(2),   // 보통
    HIGH(3);     // 높음

    private final int code;

    Priority(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Priority fromCode(int code) {
        for (Priority priority : values()) {
            if (priority.code == code) {
                return priority;
            }
        }
        return null;
    }
}
