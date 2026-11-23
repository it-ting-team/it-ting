package com.itting.user.dto;

import java.util.Optional;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * "키는 꼭 보내되 값은 null일 수 있는" 문자열을 읽는다. 키가 빠지면 null, 값이 null이면 빈 {@link Optional}이다.
 * 기본 {@code Optional} 읽기는 둘 다 빈 값으로 읽어 키가 빠진 것을 알 수 없다. 받는 쪽은 {@code @NotNull}로 빠진 키를 막는다.
 * 커뮤니티 글 수정의 {@code meetupId}(KAN-51)와 같은 방식이다.
 */
class KeyRequiredStringDeserializer extends ValueDeserializer<Optional<String>> {

	@Override
	public Optional<String> deserialize(JsonParser parser, DeserializationContext context) {
		return Optional.ofNullable(context.readValue(parser, String.class));
	}

	@Override
	public Optional<String> getNullValue(DeserializationContext context) {
		return Optional.empty();
	}

	@Override
	public Object getAbsentValue(DeserializationContext context) {
		return null;
	}

}
