package com.itting.support;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.RestClient;

/**
 * 테스트용 Mailpit 컨테이너가 받은 메일을 HTTP API로 읽는다.
 */
public class Mailpit {

	/** 받은 메일 한 통. */
	public record Message(String subject, String text) {

		private static final Pattern CODE = Pattern.compile("[0-9]{6}");

		private static final Pattern LINK = Pattern.compile("https?://[^ \r\n]+");

		/** 본문에 든 링크. */
		public String link() {
			Matcher matcher = LINK.matcher(text);
			if (!matcher.find()) {
				throw new AssertionError("메일 본문에 링크가 없습니다: " + text);
			}
			return matcher.group();
		}

		/** 본문에 든 숫자 6자리 인증 코드. */
		public String code() {
			Matcher matcher = CODE.matcher(text);
			if (!matcher.find()) {
				throw new AssertionError("메일 본문에 인증 코드가 없습니다: " + text);
			}
			return matcher.group();
		}

	}

	private final RestClient restClient;

	public Mailpit(String baseUrl) {
		this.restClient = RestClient.create(baseUrl);
	}

	public void deleteAll() {
		restClient.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
	}

	/** 이 주소로 온 메일 수. */
	public int countTo(String email) {
		return search(email).size();
	}

	/** 응답과 따로 보내는 메일을 기다린다. 이 주소로 온 메일이 {@code count}통이 될 때까지(최대 10초). */
	public Message awaitLatestTo(String email, int count) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			if (countTo(email) >= count) {
				return latestTo(email);
			}
			Thread.sleep(100);
		}
		throw new AssertionError(email + "로 메일 " + count + "통이 오지 않았습니다. 온 메일: " + countTo(email));
	}

	/** 이 주소로 온 가장 최근 메일. 없으면 실패한다. */
	@SuppressWarnings("unchecked")
	public Message latestTo(String email) {
		List<Map<String, Object>> messages = search(email);
		if (messages.isEmpty()) {
			throw new AssertionError(email + "로 온 메일이 없습니다.");
		}
		String id = (String) messages.getFirst().get("ID");
		Map<String, Object> message = restClient.get().uri("/api/v1/message/{id}", id).retrieve().body(Map.class);
		return new Message((String) message.get("Subject"), (String) message.get("Text"));
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> search(String email) {
		Map<String, Object> result = restClient.get()
				.uri(builder -> builder.path("/api/v1/search").queryParam("query", "to:\"" + email + "\"").build())
				.retrieve()
				.body(Map.class);
		return (List<Map<String, Object>>) result.get("messages");
	}

}
