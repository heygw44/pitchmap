package com.pitchmap.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class PatchFieldDeserializationTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("JSON에 필드가 없으면 요청에 없는 값(absent)으로 읽는다")
    void missingPropertyBecomesAbsent() {
        Form form = mapper.readValue("{}", Form.class);

        assertThat(form.name()).isEqualTo(PatchField.absent());
        assertThat(form.name().present()).isFalse();
        assertThat(form.count().present()).isFalse();
    }

    @Test
    @DisplayName("JSON에 null을 보내면 요청에 있고 값이 null인 필드로 읽는다")
    void explicitNullBecomesPresentNull() {
        Form form = mapper.readValue("{\"name\":null,\"count\":null}", Form.class);

        assertThat(form.name()).isEqualTo(PatchField.of(null));
        assertThat(form.name().present()).isTrue();
        assertThat(form.count()).isEqualTo(PatchField.of(null));
    }

    @Test
    @DisplayName("JSON에 값을 보내면 안쪽 타입으로 읽어 요청에 있는 값으로 감싼다")
    void valueBecomesPresentValueOfContentType() {
        Form form = mapper.readValue("{\"name\":\"새벽능선\",\"count\":3}", Form.class);

        assertThat(form.name()).isEqualTo(PatchField.of("새벽능선"));
        assertThat(form.count()).isEqualTo(PatchField.of(3));
    }

    @Test
    @DisplayName("필드마다 따로 읽어서 한 요청 안에 없는 필드, null, 값이 섞여도 각자 구분한다")
    void mixedPropertiesAreDistinguished() {
        Form form = mapper.readValue("{\"count\":null}", Form.class);

        assertThat(form.name()).isEqualTo(PatchField.absent());
        assertThat(form.count()).isEqualTo(PatchField.of(null));
    }

    @Test
    @DisplayName("안쪽 타입으로 읽을 수 없는 값이면 Jackson이 예외를 던진다")
    void valueOfWrongTypeIsRejected() {
        assertThatThrownBy(() -> mapper.readValue("{\"count\":\"many\"}", Form.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("요청에 없는 필드가 값을 가지도록 만들면 거부한다")
    void absentFieldCannotHoldValue() {
        assertThatThrownBy(() -> new PatchField<>(false, "값")).isInstanceOf(IllegalArgumentException.class);
    }

    record Form(PatchField<String> name, PatchField<Integer> count) {}
}
