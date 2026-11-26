package com.itting.realtime.service;

import java.util.Map;

/**
 * 서버끼리 주고받는 신호 한 건. "무슨 일이 어디서 생겼는지"만 담고 브라우저로 보낼 값은 담지 않는다. 값은 신호를 받은 서버가
 * 자기에게 붙어 있는 사람의 것만 계산한다({@link EventStreamRelay}).
 *
 * @param type 신호의 종류. 브라우저로 보내는 소식의 이름과 같다(예: {@code CHAT_MESSAGE_CREATED})
 * @param args 그 일을 가리키는 id(예: {@code chatRoomId}, {@code messageId}, {@code userId})
 */
public record EventStreamSignal(String type, Map<String, Long> args) {

	/**
	 * @throws IllegalArgumentException 그 이름의 값이 없으면
	 */
	public Long arg(String name) {
		Long value = args == null ? null : args.get(name);
		if (value == null) {
			throw new IllegalArgumentException("신호에 " + name + " 값이 없습니다.");
		}
		return value;
	}

}
