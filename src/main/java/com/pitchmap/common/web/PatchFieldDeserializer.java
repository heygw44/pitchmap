package com.pitchmap.common.web;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.util.AccessPattern;

/**
 * JSON 값을 {@link PatchField}로 읽는다. Jackson은 경우마다 이 클래스의 다른 메서드를 부른다.
 *
 * <ul>
 *   <li>값이 있으면 {@link #deserialize}를 부른다. 이 클래스는 안쪽 타입(예: {@code PatchField<String>}의 {@code String})의
 *       기본 역직렬화기로 값을 읽고 {@code PatchField.of(값)}으로 감싼다.
 *   <li>{@code null}이 오면 {@link #getNullValue}를 부르고, 이 클래스는 {@code PatchField.of(null)}을 돌려준다.
 *   <li>레코드 생성자에 넘길 필드가 JSON에 없으면 {@link #getAbsentValue}를 부르고, 이 클래스는 {@link PatchField#absent()}를 돌려준다.
 * </ul>
 *
 * <p>{@link PatchField}에 붙인 {@code @JsonDeserialize}로 Jackson이 이 클래스를 찾는다. 처음에는 안쪽 타입을 모르는 상태로 만들어지고,
 * 필드마다 {@link #createContextual}에서 안쪽 타입에 맞는 역직렬화기를 가진 새 인스턴스를 받는다.
 */
final class PatchFieldDeserializer extends ValueDeserializer<PatchField<?>> {

    private static final PatchField<?> EXPLICIT_NULL = PatchField.of(null);

    private final ValueDeserializer<Object> contentDeserializer;

    // Jackson이 @JsonDeserialize(using = ...)를 보고 이 생성자로 인스턴스를 만든다.
    public PatchFieldDeserializer() {
        this(null);
    }

    private PatchFieldDeserializer(ValueDeserializer<Object> contentDeserializer) {
        this.contentDeserializer = contentDeserializer;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType patchFieldType = ctxt.getContextualType();
        if (patchFieldType == null && property != null) {
            patchFieldType = property.getType();
        }
        if (patchFieldType == null) {
            return this;
        }
        JavaType contentType = patchFieldType.containedTypeOrUnknown(0);
        return new PatchFieldDeserializer(ctxt.findContextualValueDeserializer(contentType, property));
    }

    @Override
    public PatchField<?> deserialize(JsonParser p, DeserializationContext ctxt) {
        if (contentDeserializer == null) {
            throw new IllegalStateException("PatchField의 안쪽 타입을 알 수 없어 값을 읽을 수 없습니다.");
        }
        return PatchField.of(contentDeserializer.deserialize(p, ctxt));
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return EXPLICIT_NULL;
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return AccessPattern.CONSTANT;
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return PatchField.absent();
    }

    @Override
    public Class<?> handledType() {
        return PatchField.class;
    }
}
