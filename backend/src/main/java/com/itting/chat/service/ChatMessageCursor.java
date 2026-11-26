package com.itting.chat.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.itting.global.error.BusinessException;

/**
 * 메시지 목록의 커서. 프론트엔드는 내용을 보지 않고 받은 그대로 다시 보낸다.
 *
 * <p>메시지는 새 것부터(id가 큰 것부터) 주므로 마지막으로 준(가장 오래된) 메시지의 id만 담는다. 메시지는 고치거나 지우지 않고
 * 새 메시지는 더 큰 id로 생기므로, 이어 받는 사이에 메시지가 와도 이미 받은 것이 다시 오지 않는다.
 *
 * @param id 마지막으로 준 메시지의 id. 다음에는 이보다 작은 것을 준다
 */
record ChatMessageCursor(long id) {

	String encode() {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @throws BusinessException 읽을 수 없는 커서면 {@code 400 INVALID_INPUT}
	 */
	static ChatMessageCursor decode(String value) {
		try {
			long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
			if (id <= 0) {
				throw invalid();
			}
			return new ChatMessageCursor(id);
		}
		catch (IllegalArgumentException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return BusinessException.invalidInput("cursor", "앞 응답의 nextCursor를 그대로 보내야 합니다.");
	}

}
