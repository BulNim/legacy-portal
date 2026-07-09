package com.ktds.portal.approval.domain;

/**
 * [리팩토링] ApprovalService.amountGrade()의 금액 등급 규칙을 enum으로 Move Method (docs/4-12).
 *
 * 레거시(보존 대상):
 *   a >= 10,000,000 → "S" (1000만원)
 *   a >=  1,000,000 → "A" (100만원)
 *   a >=    100,000 → "B" (10만원)
 *   그 외           → "C"
 * [보존 대상] 반환 문자열("S"/"A"/"B"/"C")·경계값은 레거시와 100% 동일.
 * [스멜 해소] 흩어져 있던 등급 기준액 매직넘버를 각 상수의 threshold로 이름 붙여 모았다.
 */
public enum AmountGrade {
    S(10_000_000L),
    A(1_000_000L),
    B(100_000L),
    C(0L);

    private final long threshold;

    AmountGrade(long threshold) {
        this.threshold = threshold;
    }

    /** 이 등급의 하한 금액(원). Approval.submit()의 고액 기준(A=100만원) 등에서 재사용. */
    public long threshold() {
        return threshold;
    }

    /** 금액(원)에 해당하는 등급. 큰 기준부터 검사해 레거시 if-else 사슬과 동일하게 판정한다. */
    public static AmountGrade of(long amount) {
        if (amount >= S.threshold) return S;
        if (amount >= A.threshold) return A;
        if (amount >= B.threshold) return B;
        return C;
    }
}
