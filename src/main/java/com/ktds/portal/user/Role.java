package com.ktds.portal.user;

/**
 * [리팩토링] 권한(role) 매직넘버(1/2/3)를 enum 상수로 (docs/4-12 BL-02·BL-06, ApprovalAction과 동일 방식).
 * [보존 대상] User.role 필드·getRole()·생성자·JSON·DB는 레거시 그대로 int. 이 enum은 서비스의
 * "role >= 2"(팀장 이상) 판정에서 매직넘버 2를 {@code Role.MANAGER.code()} 로 대체하는 용도로만 쓴다.
 */
public enum Role {
    STAFF(1),       // 사원
    MANAGER(2),     // 팀장
    EXECUTIVE(3);   // 임원

    private final int code;

    Role(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Role fromCode(int code) {
        for (Role role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }
}
