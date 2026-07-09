package com.ktds.portal.approval.domain;

/**
 * [리팩토링] 결재 유형 매직넘버(1/2/3/4)를 enum 상수로 (docs/4-12 BL-02, ApprovalAction과 동일 방식).
 * [보존 대상] Approval.type 필드·getType()·JSON·DB는 레거시 그대로 int. 이 enum은 ApprovalService
 * 내부 비교(예: 고액 지출 우선순위 상향)에서 fromCode()로 변환해 쓰는 용도로만 존재한다.
 */
public enum ApprovalType {
    EXPENSE(1),    // 지출
    VACATION(2),   // 휴가
    PURCHASE(3),   // 구매
    ETC(4);        // 기타

    private final int code;

    ApprovalType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ApprovalType fromCode(int code) {
        for (ApprovalType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
