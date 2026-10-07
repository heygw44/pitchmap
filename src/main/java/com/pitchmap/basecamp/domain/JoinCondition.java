package com.pitchmap.basecamp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 캠프 리더가 거는 합류 조건이다. 값이 없으면 그 조건은 걸지 않은 것이다.
 *
 * <p>이 클래스는 조건 값이 올바른지만 검사한다. 신청자가 조건을 채웠는지는 신청자의 본인확인 정보가 필요해서 이 클래스가 판정하지 않는다.
 * 연령대는 20, 30처럼 십 단위 숫자이고 60은 60대 이상을 뜻한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JoinCondition {

    public static final int MIN_TRUST_LEVEL = 1;
    public static final int MAX_TRUST_LEVEL = 2;
    public static final int MIN_AGE_GROUP = 20;
    public static final int MAX_AGE_GROUP = 60;
    public static final int AGE_GROUP_STEP = 10;

    @Column(name = "min_trust_level")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer minTrustLevel;

    @Column(name = "age_group_min")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer ageGroupMin;

    @Column(name = "age_group_max")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer ageGroupMax;

    @Column(name = "same_gender_only")
    private boolean sameGenderOnly;

    @Column(name = "required_gender")
    @Enumerated(EnumType.STRING)
    private JoinGender requiredGender;

    private JoinCondition(
            Integer minTrustLevel,
            Integer ageGroupMin,
            Integer ageGroupMax,
            boolean sameGenderOnly,
            JoinGender requiredGender) {
        this.minTrustLevel = minTrustLevel;
        this.ageGroupMin = ageGroupMin;
        this.ageGroupMax = ageGroupMax;
        this.sameGenderOnly = sameGenderOnly;
        this.requiredGender = requiredGender;
    }

    /** 호출하면 아무 조건도 걸지 않은 합류 조건을 만든다. */
    public static JoinCondition none() {
        return new JoinCondition(null, null, null, false, null);
    }

    /** 호출하면 최소 신뢰 단계를 건 새 조건을 돌려준다. 단계가 1~2가 아니면 {@link IllegalArgumentException}을 던진다. */
    public JoinCondition withMinTrustLevel(int level) {
        if (level < MIN_TRUST_LEVEL || level > MAX_TRUST_LEVEL) {
            throw new IllegalArgumentException("최소 신뢰 단계는 " + MIN_TRUST_LEVEL + " 또는 " + MAX_TRUST_LEVEL + "이어야 합니다.");
        }
        return new JoinCondition(level, ageGroupMin, ageGroupMax, sameGenderOnly, requiredGender);
    }

    /** 호출하면 연령대 범위를 건 새 조건을 돌려준다. 값이 십 단위 20~60이 아니거나 min이 max보다 크면 {@link IllegalArgumentException}을 던진다. */
    public JoinCondition withAgeGroupRange(int min, int max) {
        requireAgeGroup(min);
        requireAgeGroup(max);
        if (min > max) {
            throw new IllegalArgumentException("연령대 하한은 상한보다 클 수 없습니다.");
        }
        return new JoinCondition(minTrustLevel, min, max, sameGenderOnly, requiredGender);
    }

    /** 호출하면 gender와 같은 성별만 받는 새 조건을 돌려준다. gender가 null이면 {@link IllegalArgumentException}을 던진다. */
    public JoinCondition withSameGenderOnly(JoinGender gender) {
        if (gender == null) {
            throw new IllegalArgumentException("동성만 받으려면 캠프 리더의 성별이 필요합니다.");
        }
        return new JoinCondition(minTrustLevel, ageGroupMin, ageGroupMax, true, gender);
    }

    private static void requireAgeGroup(int ageGroup) {
        boolean inRange = ageGroup >= MIN_AGE_GROUP && ageGroup <= MAX_AGE_GROUP;
        if (!inRange || ageGroup % AGE_GROUP_STEP != 0) {
            throw new IllegalArgumentException("연령대는 " + MIN_AGE_GROUP + "~" + MAX_AGE_GROUP + " 사이의 십 단위여야 합니다.");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JoinCondition that
                && sameGenderOnly == that.sameGenderOnly
                && Objects.equals(minTrustLevel, that.minTrustLevel)
                && Objects.equals(ageGroupMin, that.ageGroupMin)
                && Objects.equals(ageGroupMax, that.ageGroupMax)
                && requiredGender == that.requiredGender;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minTrustLevel, ageGroupMin, ageGroupMax, sameGenderOnly, requiredGender);
    }
}
