package com.ktds.portal.approval.domain;

/**
 * [리팩토링] 우선순위 매직넘버(1/2/3)를 enum 상수로 (docs/4-12 BL-02, ApprovalAction과 동일 방식).
 * [보존 대상] Approval.priority 필드·getPriority()·JSON·DB는 레거시 그대로 int. 이 enum은
 * ApprovalService에서 우선순위 값을 세팅/비교할 때 code()로 변환해 쓰는 용도로만 존재한다.
 * [이름 규칙] 제네릭 단독명 Priority → 소속 도메인을 드러내는 ApprovalPriority로 rename(CLAUDE.md 네이밍 규칙).
 */
public enum ApprovalPriority {
    LOW(1),      // 낮음
    NORMAL(2),   // 보통
    HIGH(3);     // 높음

    private final int code;

    ApprovalPriority(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ApprovalPriority fromCode(int code) {
        for (ApprovalPriority priority : values()) {
            if (priority.code == code) {
                return priority;
            }
        }
        return null;
    }
}
