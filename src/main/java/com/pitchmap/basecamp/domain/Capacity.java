package com.pitchmap.basecamp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 캠프 리더를 포함한 베이스캠프 정원이다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Capacity {

    public static final int MIN = 2;
    public static final int MAX = 6;

    // 컬럼이 TINYINT라서, Hibernate가 기본 SMALLINT로 보고 스키마 검증에서 어긋나지 않게 JDBC 타입을 지정한다.
    @Column(name = "capacity")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private short value;

    private Capacity(int value) {
        this.value = (short) value;
    }

    /** 호출하면 정원을 만든다. 값이 {@value #MIN}~{@value #MAX}명을 벗어나면 {@link BasecampException}을 던진다. */
    public static Capacity of(int value) {
        if (value < MIN || value > MAX) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_CAPACITY_INVALID);
        }
        return new Capacity(value);
    }

    /** 이 정원이 other보다 작으면 true다. */
    public boolean isLessThan(Capacity other) {
        return value < other.value;
    }

    /** headcount명이 이미 정원을 채웠으면 true다. */
    public boolean isFilledBy(int headcount) {
        return headcount >= value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Capacity capacity && value == capacity.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
