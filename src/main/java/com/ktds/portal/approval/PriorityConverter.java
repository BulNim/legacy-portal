package com.ktds.portal.approval;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] Priority enum ↔ DB 정수 컬럼 매핑 (docs/4-12 BL-02).
 * [보존 대상] DB에는 기존 정수(1/2/3)를 그대로 저장 — @Enumerated(STRING/ORDINAL) 금지.
 */
@Converter
public class PriorityConverter implements AttributeConverter<Priority, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Priority priority) {
        return priority == null ? null : priority.code();
    }

    @Override
    public Priority convertToEntityAttribute(Integer code) {
        return code == null ? null : Priority.fromCode(code);
    }
}
