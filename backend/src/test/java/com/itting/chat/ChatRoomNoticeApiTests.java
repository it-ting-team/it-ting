package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 대화방의 공지: {@code PUT · DELETE /api/v1/chat-rooms/{chatRoomId}/notice}. 공지는 대화방에 하나뿐이고 주최자만 쓰고
 * 고치고 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRoomNoticeApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	MutableClock clock;

	Instant now;

	long host;

	long member;

	long stranger;

	Cookie hostSession;

	Cookie memberSession;

	Cookie strangerSession;

	long room;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		host = steps.createMember("host@example.com", "밤코딩");
		member = steps.createMember("member@example.com", "두더지왕");
		stranger = steps.createMember("stranger@example.com", "새벽산책");
		hostSession = steps.login(mockMvc, "host@example.com");
		memberSession = steps.login(mockMvc, "member@example.com");
		strangerSession = steps.login(mockMvc, "stranger@example.com");
		long meetup = steps.insertMeetup(host, "알고리즘 스터디", now.plus(Duration.ofDays(3)), 6, null);
		steps.apply(meetup, member, "CONFIRMED");
		room = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofDays(1)));
		chat.join(room, member, now.minus(Duration.ofHours(20)));
	}

	// ---- 쓰기 · 고치기 ----

	@Test
	void 주최자가_공지를_쓰고_고치면_대화방_조회에_보인다() throws Exception {
		write(hostSession, room, "매주 토요일 오후 2시에 모여요. 노트북을 가져오세요.")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").value((Object) null))
				.andExpect(jsonPath("$.data.notice").value("매주 토요일 오후 2시에 모여요. 노트북을 가져오세요."));
		detail(memberSession, room)
				.andExpect(jsonPath("$.data.notice").value("매주 토요일 오후 2시에 모여요. 노트북을 가져오세요."));

		// 공지는 하나뿐이라 다시 쓰면 바뀐다.
		write(hostSession, room, "이번 주는 쉬어요.").andExpect(status().isOk());
		detail(memberSession, room).andExpect(jsonPath("$.data.notice").value("이번 주는 쉬어요."));
		assertThat(chat.room(room)).containsEntry("notice", "이번 주는 쉬어요.");
		// 공지는 메시지가 아니다.
		assertThat(chat.messages(room)).isEmpty();
	}

	@Test
	void 공지는_200자까지이고_비었거나_공백뿐이면_400이다() throws Exception {
		write(hostSession, room, "가".repeat(200)).andExpect(status().isOk());
		// 이모지도 한 글자로 센다.
		write(hostSession, room, "😀".repeat(200)).andExpect(status().isOk());

		for (String body : List.of(content("가".repeat(201)), content(""), content("   "), "{}", "{ \"content\": null }")) {
			chat.sync();
			mockMvc.perform(put("/api/v1/chat-rooms/{id}/notice", room)
							.cookie(hostSession)
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
					.andExpect(jsonPath("$.error.fields[0].field").value("content"));
		}
		assertThat(chat.room(room)).containsEntry("notice", "😀".repeat(200));
	}

	@Test
	void 주최자가_아닌_참여자와_일대일_대화방은_403이다() throws Exception {
		long direct = chat.insertDirectRoom(host, member, now.minus(Duration.ofHours(1)));

		write(memberSession, room, "참여자가 쓴 공지")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		write(hostSession, direct, "1:1에는 공지가 없다")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		remove(memberSession, room)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		remove(hostSession, direct).andExpect(status().isForbidden());

		assertThat(chat.room(room).get("notice")).isNull();
	}

	@Test
	void 들어갈_수_없는_대화방은_404다() throws Exception {
		write(strangerSession, room, "남이 쓴 공지")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		remove(strangerSession, room)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		write(hostSession, 999_999_999L, "없는 방").andExpect(status().isNotFound());
		remove(hostSession, 999_999_999L).andExpect(status().isNotFound());

		mockMvc.perform(put("/api/v1/chat-rooms/{id}/notice", room)
						.contentType(MediaType.APPLICATION_JSON)
						.content(content("로그인 안 함")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/chat-rooms/{id}/notice", room)).andExpect(status().isUnauthorized());
	}

	// ---- 지우기 ----

	@Test
	void 주최자가_공지를_지우면_null이_되고_공지가_없어도_성공이다() throws Exception {
		chat.setNotice(room, "지울 공지");

		remove(hostSession, room)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value((Object) null))
				.andExpect(jsonPath("$.error").value((Object) null));
		detail(memberSession, room).andExpect(jsonPath("$.data.notice").value((Object) null));
		assertThat(chat.room(room).get("notice")).isNull();

		remove(hostSession, room).andExpect(status().isOk());
	}

	private ResultActions write(Cookie cookie, long roomId, String content) throws Exception {
		chat.sync();
		ResultActions result = mockMvc.perform(put("/api/v1/chat-rooms/{id}/notice", roomId)
				.cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(content(content)));
		chat.sync();
		return result;
	}

	private ResultActions remove(Cookie cookie, long roomId) throws Exception {
		chat.sync();
		ResultActions result = mockMvc.perform(delete("/api/v1/chat-rooms/{id}/notice", roomId).cookie(cookie));
		chat.sync();
		return result;
	}

	private ResultActions detail(Cookie cookie, long roomId) throws Exception {
		chat.sync();
		return mockMvc.perform(get("/api/v1/chat-rooms/{id}", roomId).cookie(cookie));
	}

	private static String content(String content) {
		return "{ \"content\": \"" + content + "\" }";
	}

}
