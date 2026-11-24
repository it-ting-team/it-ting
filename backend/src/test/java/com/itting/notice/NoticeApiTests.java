package com.itting.notice;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.itting.notice.domain.Notice;
import com.itting.notice.domain.NoticeCategory;
import com.itting.notice.repository.NoticeRepository;
import com.itting.support.NotificationSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/notices}, {@code GET /api/v1/notices/{noticeId}}, 그리고 공지 알림의 제목.
 * 공지를 올리는 API(11 운영 · 관리자)가 아직 없어서 공지는 DB에 바로 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NoticeApiTests {

	private static final String PASSWORD = "mole1234!";

	private static final Instant T0 = Instant.parse("2026-11-05T06:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	NotificationSteps steps;

	@Autowired
	NoticeRepository noticeRepository;

	long me;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", PASSWORD, "두더지왕");
		session = login("me@example.com");
	}

	// GET /api/v1/notices

	@Test
	void 삭제한_공지는_빼고_최신순으로_주며_같은_시각이면_id가_큰_것이_먼저다() throws Exception {
		long old = insertNotice("NOTICE", "서비스 점검 안내(11월 8일 새벽)", "점검합니다.", T0, null);
		long sameTimeFirst = insertNotice("UPDATE", "모임 캘린더가 새로 생겼어요", "캘린더가 생겼어요.", T0.plus(Duration.ofDays(13)), null);
		long sameTimeSecond = insertNotice("EVENT", "첫 모임 열기 이벤트", "이벤트예요.", T0.plus(Duration.ofDays(13)), null);
		insertNotice("NOTICE", "지운 공지", "지웠어요.", T0.plus(Duration.ofDays(20)), T0.plus(Duration.ofDays(21)));

		mockMvc.perform(get("/api/v1/notices").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[*].id")
						.value(contains((int) sameTimeSecond, (int) sameTimeFirst, (int) old)))
				.andExpect(jsonPath("$.data.items[0].category").value("EVENT"))
				.andExpect(jsonPath("$.data.items[0].title").value("첫 모임 열기 이벤트"))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-18T06:00:00Z"))
				.andExpect(jsonPath("$.data.items[1].category").value("UPDATE"))
				.andExpect(jsonPath("$.data.items[2].category").value("NOTICE"))
				// 목록에는 본문을 담지 않는다.
				.andExpect(jsonPath("$.data.items[0].content").doesNotExist())
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(10))
				.andExpect(jsonPath("$.data.totalItems").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void 한_페이지에_size개씩_주고_페이지를_넘으면_빈_목록이다() throws Exception {
		long[] ids = new long[5];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = insertNotice("NOTICE", "공지 " + i, "본문 " + i, T0.plus(Duration.ofHours(i)), null);
		}

		mockMvc.perform(get("/api/v1/notices").param("page", "2").param("size", "2").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) ids[2], (int) ids[1])))
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalItems").value(5))
				.andExpect(jsonPath("$.data.totalPages").value(3));
		mockMvc.perform(get("/api/v1/notices").param("page", "3").param("size", "2").cookie(session))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) ids[0])));
		mockMvc.perform(get("/api/v1/notices").param("page", "4").param("size", "2").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0))
				.andExpect(jsonPath("$.data.totalItems").value(5))
				.andExpect(jsonPath("$.data.totalPages").value(3));
	}

	@Test
	void 공지가_없으면_items가_빈_목록이고_totalItems와_totalPages가_0이다() throws Exception {
		mockMvc.perform(get("/api/v1/notices").cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": { "items": [], "page": 1, "size": 10, "totalItems": 0, "totalPages": 0 },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void size가_범위를_벗어나거나_숫자가_아니면_400_INVALID_INPUT이다() throws Exception {
		mockMvc.perform(get("/api/v1/notices").param("size", "51").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("size")));
		mockMvc.perform(get("/api/v1/notices").param("size", "0").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/notices").param("page", "0").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("page")));
		mockMvc.perform(get("/api/v1/notices").param("page", "abc").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		// 50은 경계의 허용값이다.
		mockMvc.perform(get("/api/v1/notices").param("size", "50").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.size").value(50));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		long noticeId = insertNotice("NOTICE", "공지", "본문", T0, null);

		mockMvc.perform(get("/api/v1/notices"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403이다() throws Exception {
		long noticeId = insertNotice("NOTICE", "공지", "본문", T0, null);
		testUsers.createEmailUser("new@example.com", PASSWORD);
		Cookie newcomer = login("new@example.com");

		mockMvc.perform(get("/api/v1/notices").cookie(newcomer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
		mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId).cookie(newcomer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	// GET /api/v1/notices/{noticeId}

	@Test
	void 상세는_본문까지_준다() throws Exception {
		long noticeId = insertNotice("UPDATE", "모임 캘린더가 새로 생겼어요",
				"내 모임에서 캘린더를 열 수 있어요.\n참여하는 모임과 내가 연 모임이 날짜별로 보입니다.", Instant.parse("2026-11-18T02:00:00Z"),
				null);

		mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": {
						    "id": %d,
						    "category": "UPDATE",
						    "title": "모임 캘린더가 새로 생겼어요",
						    "content": "내 모임에서 캘린더를 열 수 있어요.\\n참여하는 모임과 내가 연 모임이 날짜별로 보입니다.",
						    "createdAt": "2026-11-18T02:00:00Z"
						  },
						  "error": null
						}
						""".formatted(noticeId), JsonCompareMode.STRICT));
	}

	@Test
	void 엔티티로_만든_공지도_읽고_삭제하면_404가_된다() throws Exception {
		Notice notice = noticeRepository.saveAndFlush(Notice.create(NoticeCategory.EVENT, "엔티티 공지", "본문\n둘째 줄"));

		mockMvc.perform(get("/api/v1/notices/{noticeId}", notice.getId()).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.category").value("EVENT"))
				.andExpect(jsonPath("$.data.title").value("엔티티 공지"))
				.andExpect(jsonPath("$.data.content").value("본문\n둘째 줄"))
				.andExpect(jsonPath("$.data.createdAt").isString());

		notice.delete(Instant.now());
		noticeRepository.saveAndFlush(notice);

		mockMvc.perform(get("/api/v1/notices/{noticeId}", notice.getId()).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOTICE_NOT_FOUND"));
	}

	@Test
	void 없는_공지와_삭제한_공지는_404_NOTICE_NOT_FOUND다() throws Exception {
		long deleted = insertNotice("NOTICE", "지운 공지", "지웠어요.", T0, T0.plus(Duration.ofDays(1)));

		mockMvc.perform(get("/api/v1/notices/{noticeId}", deleted).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("NOTICE_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/notices/{noticeId}", 987654321L).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOTICE_NOT_FOUND"));
	}

	@Test
	void 공지_id가_숫자가_아니면_400_INVALID_INPUT이다() throws Exception {
		mockMvc.perform(get("/api/v1/notices/{noticeId}", "abc").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	// 공지 알림

	@Test
	void 공지_알림의_제목은_공지의_제목이고_삭제한_공지는_null이다() throws Exception {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		long live = insertNotice("NOTICE", "서비스 점검 안내(11월 8일 새벽)", "점검합니다.", T0, null);
		long deleted = insertNotice("NOTICE", "지운 공지", "지웠어요.", T0, T0.plus(Duration.ofDays(1)));
		steps.insert(me, "NOTICE_PUBLISHED", "NOTICE", live, null, now.minus(Duration.ofMinutes(1)), null);
		steps.insert(me, "NOTICE_PUBLISHED", "NOTICE", deleted, null, now.minus(Duration.ofMinutes(2)), null);

		mockMvc.perform(get("/api/v1/me/notifications").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].type").value("NOTICE_PUBLISHED"))
				.andExpect(jsonPath("$.data.items[0].category").value("NOTICE"))
				.andExpect(jsonPath("$.data.items[0].target.type").value("NOTICE"))
				.andExpect(jsonPath("$.data.items[0].target.id").value(live))
				.andExpect(jsonPath("$.data.items[0].target.title").value("서비스 점검 안내(11월 8일 새벽)"))
				.andExpect(jsonPath("$.data.items[0].target.startsAt").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].target.id").value(deleted))
				.andExpect(jsonPath("$.data.items[1].target.title").value(nullValue()));
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recentItems[0].target.title").value("서비스 점검 안내(11월 8일 새벽)"));
	}

	@Test
	void 공지를_읽어도_공지_알림은_읽음으로_바뀌지_않는다() throws Exception {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		long noticeId = insertNotice("NOTICE", "공지", "본문", T0, null);
		long notificationId = steps.insert(me, "NOTICE_PUBLISHED", "NOTICE", noticeId, null, now, null);

		mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId).cookie(session)).andExpect(status().isOk());

		assertThat(steps.readAt(notificationId)).isNull();
	}

	private long insertNotice(String category, String title, String content, Instant createdAt, Instant deletedAt) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO notice (category, title, content, created_at, updated_at, deleted_at)
					VALUES (?, ?, ?, ?, ?, ?)
					""", new String[] { "id" });
			statement.setString(1, category);
			statement.setString(2, title);
			statement.setString(3, content);
			statement.setObject(4, utc(createdAt));
			statement.setObject(5, utc(createdAt));
			statement.setObject(6, deletedAt == null ? null : utc(deletedAt));
			return statement;
		}, keyHolder);
		return keyHolder.getKey().longValue();
	}

	/** JDBC 드라이버가 JVM 시간대로 바꾸지 않게 UTC의 날짜 · 시각 그대로 넘긴다. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private Cookie login(String email) throws Exception {
		return sessionCookie(mockMvc.perform(post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "%s", "password": "%s" }
								""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

}
