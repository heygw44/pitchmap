package com.pitchmap.common.web;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 부분 수정(PATCH) 요청의 필드 하나다. JSON에서 필드를 빼는 것과 {@code null}을 보내는 것은 뜻이 다르다.
 * 그래서 두 경우를 Java {@code null} 하나로 합치지 않고 따로 담는다.
 *
 * <ul>
 *   <li>요청 JSON에 필드가 없으면 {@link #absent()}다. 호출하는 쪽은 그 값을 바꾸지 않는다.
 *   <li>요청 JSON에 {@code "필드": null}이 있으면 {@code of(null)}이다. 호출하는 쪽은 값을 지운다(지울 수 없는 값이면 거부한다).
 *   <li>요청 JSON에 값이 있으면 {@code of(값)}이다. 호출하는 쪽은 그 값으로 바꾼다.
 * </ul>
 *
 * <p>Jackson은 {@link PatchFieldDeserializer}로 이 타입을 읽는다. 다만 Jackson이 레코드 생성자에 빠진 필드를 Java
 * {@code null}로 넘기는 경우가 있으므로, 이 타입을 구성요소로 갖는 요청 레코드는 {@code null}을 {@link #absent()}로 바꿔 둔다.
 */
@JsonDeserialize(using = PatchFieldDeserializer.class)
public record PatchField<T>(boolean present, T value) {

    private static final PatchField<?> ABSENT = new PatchField<>(false, null);

    public PatchField {
        if (!present && value != null) {
            throw new IllegalArgumentException("요청에 없는 필드는 값을 가질 수 없습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> PatchField<T> absent() {
        return (PatchField<T>) ABSENT;
    }

    public static <T> PatchField<T> of(T value) {
        return new PatchField<>(true, value);
    }
}
