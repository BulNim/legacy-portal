package com.ktds.portal.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] Role enum ↔ DB 정수 컬럼 매핑 (docs/4-12 BL-02).
 * [보존 대상] DB에는 기존 정수(1/2/3)를 그대로 저장 — @Enumerated(STRING/ORDINAL) 금지.
 */
@Converter
public class RoleConverter implements AttributeConverter<Role, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Role role) {
        return role == null ? null : role.code();
    }

    @Override
    public Role convertToEntityAttribute(Integer code) {
        return code == null ? null : Role.fromCode(code);
    }
}
