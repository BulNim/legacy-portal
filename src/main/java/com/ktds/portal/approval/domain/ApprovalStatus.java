package com.ktds.portal.approval.domain;

/**
 * [리팩토링] 결재 상태 매직넘버(0/1/2/3/9)를 enum 상수로 (docs/4-12 BL-02, ApprovalAction과 동일 방식).
 *
 * [보존 대상] Approval.status 필드와 getStatus()/setStatus()는 레거시 그대로 int(0/1/2/3/9)로 유지한다
 * (public 계약·DB 저장값·JSON·특성화 테스트 모두 불변). 이 enum은 오직 ApprovalService 내부에서
 * fromCode()로 변환해 비교/설정하는 용도로만 쓴다 — action을 ApprovalAction으로 다루는 것과 같은 패턴.
 */
public enum ApprovalStatus {
    DRAFT(0, "임시저장"),
    SUBMITTED(1, "상신"),
    APPROVED(2, "승인"),
    REJECTED(3, "반려"),
    CANCELED(9, "취소");

    private final int code;
    private final String label;

    ApprovalStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    // [리팩토링] ApprovalService.statusLabel()의 tmp+5분기 if를 대체 — enum이 라벨을 스스로 가진다.
    public String label() {
        return label;
    }

    // [보존 대상] 레거시 statusLabel()의 "알수없음" 분기와 동등한 방어 — 알 수 없는 코드값이면 null.
    public static ApprovalStatus fromCode(int code) {
        for (ApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
