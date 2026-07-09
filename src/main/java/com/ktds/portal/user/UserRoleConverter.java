package com.ktds.portal.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] UserRole enum ↔ DB 정수 컬럼 매핑 (docs/4-12 BL-02).
 * [보존 대상] DB에는 기존 정수(1/2/3)를 그대로 저장 — @Enumerated(STRING/ORDINAL) 금지.
 */
@Converter
public class UserRoleConverter implements AttributeConverter<UserRole, Integer> {

    @Override
    public Integer convertToDatabaseColumn(UserRole role) {
        return role == null ? null : role.code();
    }

    @Override
    public UserRole convertToEntityAttribute(Integer code) {
        return code == null ? null : UserRole.fromCode(code);
    }
}
