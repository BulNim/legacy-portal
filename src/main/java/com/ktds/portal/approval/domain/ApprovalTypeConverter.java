package com.ktds.portal.approval.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalType enum ↔ DB 정수 컬럼 매핑 (docs/4-12 BL-02).
 * [보존 대상] DB에는 기존 정수(1/2/3/4)를 그대로 저장 — @Enumerated(STRING/ORDINAL) 금지.
 */
@Converter
public class ApprovalTypeConverter implements AttributeConverter<ApprovalType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalType type) {
        return type == null ? null : type.code();
    }

    @Override
    public ApprovalType convertToEntityAttribute(Integer code) {
        return code == null ? null : ApprovalType.fromCode(code);
    }
}
