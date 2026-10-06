package com.pitchmap.trust.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityPropertiesTest {

    private static final String KEY_32_BYTES = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("키가 null이거나 비었거나 공백뿐이면 거부한다")
    void rejectsBlankKey() {
        assertThatThrownBy(() -> new IdentityProperties(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityProperties("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityProperties("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("키가 32바이트보다 짧으면 거부하고, 예외 메시지에 키 값을 담지 않는다")
    void rejectsShortKey() {
        String shortKey = KEY_32_BYTES.substring(0, 31);

        assertThatThrownBy(() -> new IdentityProperties(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(shortKey);
    }

    @Test
    @DisplayName("키가 정확히 32바이트이면 받고, toString에 키 값이 나오지 않는다")
    void acceptsMinimumKeyAndMasksToString() {
        IdentityProperties properties = new IdentityProperties(KEY_32_BYTES);

        assertThat(properties.toString()).doesNotContain(KEY_32_BYTES);
    }

    @Test
    @DisplayName("한글처럼 글자 수보다 바이트 수가 큰 키는 UTF-8 바이트 수로 길이를 잰다")
    void measuresUtf8Bytes() {
        assertThat(new IdentityProperties("가나다라마바사아자차카").ciHmacKey()).isNotBlank();
    }
}
