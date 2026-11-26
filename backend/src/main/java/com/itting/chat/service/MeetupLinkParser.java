package com.itting.chat.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.itting.global.config.FrontendProperties;
import org.springframework.stereotype.Component;

/**
 * 메시지 본문에서 모임 링크를 알아본다. 모임 링크는 모임 상세 화면의 주소다: {@code {itting.frontend-url}/meetups/{숫자}}.
 * 모임 상세의 "링크 복사"가 그 화면의 주소를 복사한다.
 *
 * <p>우리 프론트엔드 주소로 시작하는 것만 본다. 모임 id는 서버마다 달라서, 다른 사이트나 다른 서버의 링크를 카드로 바꾸면
 * 엉뚱한 모임이 보인다. 숫자 뒤에 글자가 이어지는 주소({@code /meetups/12abc})는 모임 링크가 아니다.
 */
@Component
class MeetupLinkParser {

	private final Pattern link;

	MeetupLinkParser(FrontendProperties frontend) {
		this.link = Pattern.compile(Pattern.quote(frontend.frontendUrl()) + "/meetups/(\\d{1,18})(?![0-9A-Za-z_-])");
	}

	/**
	 * @return 본문에서 처음 나오는 모임 링크의 모임 id. 본문이 없거나 링크가 없으면 null
	 */
	Long firstMeetupId(String content) {
		if (content == null) {
			return null;
		}
		Matcher matcher = link.matcher(content);
		return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
	}

}
