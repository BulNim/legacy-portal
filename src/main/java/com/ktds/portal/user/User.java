package com.ktds.portal.user;

import jakarta.persistence.*;

/**
 * 사용자 엔티티.
 * [리팩토링] role 필드를 UserRole enum으로 전환 + AttributeConverter로 DB엔 정수 그대로 저장(docs/4-12 BL-02).
 * [보존 대상] 생성자·getRole()·setRole()는 레거시처럼 int 시그니처 유지(public 계약·DB·JSON·테스트 불변).
 * 내부 필드만 enum이고 경계에서 code()/fromCode()로 변환한다. "role >= 2"(팀장 이상) 판정의 매직넘버 2는
 * 호출부(ApprovalService/NoticeService)에서 {@code UserRole.MANAGER.code()} 로 대체한다(BL-06).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    @Convert(converter = UserRoleConverter.class)
    private UserRole role;       // DB: 1=사원 2=팀장 3=임원
    private String dept;

    public User() {}

    public User(String name, String email, int role, String dept) {
        this.name = name;
        this.email = email;
        this.role = UserRole.fromCode(role);   // [보존 대상] 생성자는 int를 받아 내부 enum으로 변환(호출 형태 유지)
        this.dept = dept;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    // [보존 대상] int 시그니처 유지 — 내부 enum과 code()/fromCode()로 변환
    public int getRole() { return role.code(); }
    public void setRole(int role) { this.role = UserRole.fromCode(role); }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }

    // [리팩토링] "role >= 2"(팀장 이상) 권한 판정을 도메인 메서드로 캡슐화(BL-06). 승인/반려 분기의 복붙 조건을 대체.
    public boolean isManagerOrAbove() {
        return role.code() >= UserRole.MANAGER.code();
    }
}
