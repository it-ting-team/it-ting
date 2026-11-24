package com.itting.community.service;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;

import com.itting.global.error.BusinessException;

/**
 * 댓글 목록의 커서. 프론트엔드는 내용을 보지 않고 받은 그대로 다시 보낸다.
 *
 * <p>댓글은 등록한 순서(쓴 시각, 같으면 id)로 주므로 마지막으로 준 댓글의 자리만 담는다. 이어 받는 사이에 새 댓글이 달리면
 * 뒤에 붙고, 지워진 댓글은 건너뛰어도 이미 받은 댓글이 다시 오지 않는다.
 *
 * @param createdAt 마지막으로 준 댓글의 쓴 시각
 * @param id 마지막으로 준 댓글의 id
 */
record CommentCursor(Instant createdAt, Long id) {

	private static final String SEPARATOR = "|";

	String encode() {
		String raw = String.join(SEPARATOR, createdAt.toString(), String.valueOf(id));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @throws BusinessException 읽을 수 없는 커서면 {@code 400 INVALID_INPUT}
	 */
	static CommentCursor decode(String value) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
			String[] parts = raw.split("\\" + SEPARATOR, -1);
			if (parts.length != 2) {
				throw invalid();
			}
			return new CommentCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
		}
		catch (IllegalArgumentException | DateTimeException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return BusinessException.invalidInput("cursor", "앞 응답의 nextCursor를 그대로 보내야 합니다.");
	}

}
