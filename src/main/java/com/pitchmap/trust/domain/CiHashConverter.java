package com.pitchmap.trust.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link CiHash}를 CHAR(64) 컬럼의 문자열로 바꾸고 되돌린다. */
@Converter
public class CiHashConverter implements AttributeConverter<CiHash, String> {

    @Override
    public String convertToDatabaseColumn(CiHash attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CiHash convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new CiHash(dbData);
    }
}
