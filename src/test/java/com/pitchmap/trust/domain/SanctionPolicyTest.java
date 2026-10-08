package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SanctionPolicyTest {

    @Test
    @DisplayName("[SN-10] 확정된 제재가 없으면 1단계(경고), 그 위로는 한 단계씩 올라가 4단계(영구 정지)에서 멈춘다")
    void levelRisesByOneAndStopsAtFour() {
        assertThat(SanctionPolicy.nextLevel(null)).isEqualTo(1);
        assertThat(SanctionPolicy.nextLevel(1)).isEqualTo(2);
        assertThat(SanctionPolicy.nextLevel(2)).isEqualTo(3);
        assertThat(SanctionPolicy.nextLevel(3)).isEqualTo(4);
        assertThat(SanctionPolicy.nextLevel(4)).isEqualTo(4);
    }

    @Test
    @DisplayName("[SN-10] 단계는 경고, 7일 정지, 30일 정지, 영구 정지에 차례로 대응한다")
    void levelsMapToTypes() {
        assertThat(SanctionPolicy.typeOf(1)).isEqualTo(SanctionType.WARNING);
        assertThat(SanctionPolicy.typeOf(2)).isEqualTo(SanctionType.SUSPEND_7D);
        assertThat(SanctionPolicy.typeOf(3)).isEqualTo(SanctionType.SUSPEND_30D);
        assertThat(SanctionPolicy.typeOf(4)).isEqualTo(SanctionType.PERMANENT);
        assertThat(SanctionPolicy.levelOf(SanctionType.SUSPEND_30D)).isEqualTo(3);
        assertThatThrownBy(() -> SanctionPolicy.typeOf(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SanctionPolicy.typeOf(5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SanctionPolicy.levelOf(SanctionType.TEMPORARY_72H))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[SN-10] 요청한 종류가 계산된 다음 단계와 같으면 그 단계를 돌려준다")
    void acceptsComputedType() {
        assertThat(SanctionPolicy.decideLevel(null, SanctionType.WARNING)).isEqualTo(1);
        assertThat(SanctionPolicy.decideLevel(1, SanctionType.SUSPEND_7D)).isEqualTo(2);
        assertThat(SanctionPolicy.decideLevel(2, SanctionType.SUSPEND_30D)).isEqualTo(3);
        assertThat(SanctionPolicy.decideLevel(3, SanctionType.PERMANENT)).isEqualTo(4);
    }

    @Test
    @DisplayName("[SN-10] 이미 영구 정지 단계까지 갔으면 영구 정지만 다시 내릴 수 있다")
    void afterPermanentOnlyPermanentIsAllowed() {
        assertThat(SanctionPolicy.decideLevel(4, SanctionType.PERMANENT)).isEqualTo(4);
        assertInvalidInput(4, SanctionType.WARNING);
    }

    @Test
    @DisplayName("[SN-11] 심각한 위반이면 단계를 건너뛰어 영구 정지를 곧바로 지정할 수 있다")
    void permanentCanBeRequestedDirectly() {
        assertThat(SanctionPolicy.decideLevel(null, SanctionType.PERMANENT)).isEqualTo(4);
        assertThat(SanctionPolicy.decideLevel(1, SanctionType.PERMANENT)).isEqualTo(4);
    }

    @Test
    @DisplayName("[SN-10] 계산된 다음 단계와 다른 종류를 요청하면 INVALID_INPUT이다")
    void rejectsTypeDifferentFromComputed() {
        assertInvalidInput(null, SanctionType.SUSPEND_7D);
        assertInvalidInput(1, SanctionType.WARNING);
        assertInvalidInput(1, SanctionType.SUSPEND_30D);
        assertInvalidInput(2, SanctionType.SUSPEND_7D);
    }

    @Test
    @DisplayName("[SN-05] 임시 정지와 null은 요청할 수 없다")
    void rejectsTemporaryAndNull() {
        assertInvalidInput(null, SanctionType.TEMPORARY_72H);
        assertInvalidInput(null, null);
    }

    private static void assertInvalidInput(Integer highestLevel, SanctionType requested) {
        assertThatThrownBy(() -> SanctionPolicy.decideLevel(highestLevel, requested))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
