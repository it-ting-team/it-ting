package com.itting.notification.service;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;

import com.itting.global.error.BusinessException;

/**
 * 알림 목록의 커서. 프론트엔드는 내용을 보지 않고 받은 그대로 다시 보낸다.
 *
 * <p>이어 받는 동안 정렬이 바뀌지 않도록 첫 요청 시각({@code asOf})을 담는다. 안 읽음 · 읽음은 그때의 상태로 보고, 그 뒤에
 * 생긴 알림은 주지 않는다. 그래서 사이에 알림을 읽어도 이미 받은 알림이 다시 오지 않는다.
 *
 * @param asOf 첫 요청 시각
 * @param unread 마지막으로 준 알림이 안 읽은 구간에 있었는지
 * @param createdAt 마지막으로 준 알림의 시각
 * @param id 마지막으로 준 알림의 id
 */
record NotificationCursor(Instant asOf, boolean unread, Instant createdAt, Long id) {

	private static final String SEPARATOR = "|";

	String encode() {
		String raw = String.join(SEPARATOR, asOf.toString(), unread ? "U" : "R", createdAt.toString(),
				String.valueOf(id));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @throws BusinessException 읽을 수 없는 커서면 {@code 400 INVALID_INPUT}
	 */
	static NotificationCursor decode(String value) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
			String[] parts = raw.split("\\" + SEPARATOR, -1);
			if (parts.length != 4 || !(parts[1].equals("U") || parts[1].equals("R"))) {
				throw invalid();
			}
			return new NotificationCursor(Instant.parse(parts[0]), parts[1].equals("U"), Instant.parse(parts[2]),
					Long.parseLong(parts[3]));
		}
		catch (IllegalArgumentException | DateTimeException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return BusinessException.invalidInput("cursor", "앞 응답의 nextCursor를 그대로 보내야 합니다.");
	}

}
