package com.ktds.portal.approval.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalStatus enum ↔ DB 정수 컬럼 매핑 (docs/4-12 BL-02).
 * [보존 대상] DB에는 기존 정수(0/1/2/3/9)를 그대로 저장 — @Enumerated(STRING/ORDINAL) 금지.
 */
@Converter
public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalStatus status) {
        return status == null ? null : status.code();
    }

    @Override
    public ApprovalStatus convertToEntityAttribute(Integer code) {
        return code == null ? null : ApprovalStatus.fromCode(code);
    }
}
