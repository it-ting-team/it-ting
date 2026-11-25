package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.itting.support.CommunitySteps;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글에 모임을 연결하는 일: 연결할 수 있는 모임 목록, 글쓰기 · 글 수정의 검사, 글 상세의 모임 카드.
 * 모임은 DB에 바로 넣는다. 지금 시각은 {@link MutableClock}으로 정하고, 모임의 시작 시각은 지금에서 얼마나 떨어졌는지로 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class PostMeetupLinkApiTests {

	private static final String LINKABLE = "/api/v1/me/linkable-meetups";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	@Autowired
	MutableClock clock;

	Instant now;

	long me;

	long other;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "나두더지");
		other = steps.createMember("other@example.com", "두더지왕");
		session = steps.login(mockMvc, "me@example.com");
	}

	// ---------------------------------------------------------------- GET /me/linkable-meetups

	@Test
	void 모임_홍보는_내가_연_모집_중인_모임만_시작이_가까운_순으로_준다() throws Exception {
		Meetups m = prepareMeetups();

		linkable("PROMOTION")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[*].id").value(contains(id(m.soon), id(m.later))))
				.andExpect(jsonPath("$.data.items[*].recruitStatus").value(contains("OPEN", "OPEN")))
				.andExpect(jsonPath("$.data.items[0].title").value("곧 시작하는 모임"))
				.andExpect(jsonPath("$.data.items[0].category").value("EXERCISE"))
				.andExpect(jsonPath("$.data.items[0].mode").value("ONLINE"))
				.andExpect(jsonPath("$.data.items[0].place").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].endsAt").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].capacity").value(4))
				.andExpect(jsonPath("$.data.items[0].confirmedCount").value(1));
	}

	@Test
	void 모임_후기는_내가_열었거나_참여가_확정된_지난_모임만_시작이_최근인_순으로_준다() throws Exception {
		Meetups m = prepareMeetups();

		linkable("REVIEW")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id")
						.value(contains(id(m.startsNow), id(m.pastHosted), id(m.pastJoined))))
				.andExpect(jsonPath("$.data.items[*].recruitStatus").value(contains("PAST", "PAST", "PAST")));
	}

	@Test
	void 자유는_내가_열었거나_참여가_확정된_모임을_지난_것까지_시작이_최근인_순으로_준다() throws Exception {
		Meetups m = prepareMeetups();

		linkable("FREE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains(id(m.joinedUpcoming), id(m.deadlineClosed),
						id(m.later), id(m.full), id(m.soon), id(m.startsNow), id(m.pastHosted), id(m.pastJoined))))
				.andExpect(jsonPath("$.data.items[*].recruitStatus")
						.value(contains("OPEN", "CLOSED", "OPEN", "CLOSED", "OPEN", "PAST", "PAST", "PAST")))
				.andExpect(jsonPath("$.data.items[3].confirmedCount").value(2));
	}

	@Test
	void 시작_시각이_같으면_모임_홍보는_id가_작은_것부터_후기와_자유는_큰_것부터_준다() throws Exception {
		long first = steps.insertMeetup(me, "첫째", now.plus(Duration.ofDays(1)), 4, null);
		long second = steps.insertMeetup(me, "둘째", now.plus(Duration.ofDays(1)), 4, null);
		long pastFirst = steps.insertMeetup(me, "지난 첫째", now.minus(Duration.ofDays(1)), 4, null);
		long pastSecond = steps.insertMeetup(me, "지난 둘째", now.minus(Duration.ofDays(1)), 4, null);

		linkable("PROMOTION").andExpect(jsonPath("$.data.items[*].id").value(contains((int) first, (int) second)));
		linkable("REVIEW").andExpect(jsonPath("$.data.items[*].id").value(contains((int) pastSecond, (int) pastFirst)));
		linkable("FREE").andExpect(jsonPath("$.data.items[*].id")
				.value(contains((int) second, (int) first, (int) pastSecond, (int) pastFirst)));
	}

	@Test
	void 연결할_모임이_없으면_빈_목록이다() throws Exception {
		for (String category : List.of("PROMOTION", "REVIEW", "FREE")) {
			linkable(category)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items").value(empty()));
		}
	}

	@Test
	void 남의_목록에는_내가_연_모임이_섞이지_않는다() throws Exception {
		long mine = steps.insertMeetup(me, "내 모임", now.plus(Duration.ofDays(1)), 4, null);
		Cookie otherSession = steps.login(mockMvc, "other@example.com");

		mockMvc.perform(get(LINKABLE).param("postCategory", "FREE").cookie(otherSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").value(empty()));
		steps.apply(mine, other, "CONFIRMED");
		mockMvc.perform(get(LINKABLE).param("postCategory", "FREE").cookie(otherSession))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) mine)));
		mockMvc.perform(get(LINKABLE).param("postCategory", "PROMOTION").cookie(otherSession))
				.andExpect(jsonPath("$.data.items").value(empty()));
	}

	@Test
	void 연결할_모임_목록은_카테고리가_없거나_틀리면_400_로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get(LINKABLE).cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("postCategory"));
		linkable("NOTICE")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("postCategory"));
		mockMvc.perform(get(LINKABLE).param("postCategory", "FREE"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	// ---------------------------------------------------------------- POST /posts

	@Test
	void 모임_홍보에_내_모집_중인_모임을_연결해_쓰면_201이고_모임_id를_저장한다() throws Exception {
		long meetup = steps.insertMeetup(me, "한강 러닝", now.plus(Duration.ofDays(2)), 4, null);

		long postId = createdId(create("PROMOTION", String.valueOf(meetup)));

		assertThat(storedMeetupId(postId)).isEqualTo(meetup);
	}

	@Test
	void 모임_후기에_내가_열었거나_참여가_확정된_지난_모임을_연결해_쓰면_201이다() throws Exception {
		long hosted = steps.insertMeetup(me, "내가 연 지난 모임", now.minus(Duration.ofDays(1)), 4, null);
		long joined = steps.insertMeetup(other, "참여한 지난 모임", now.minus(Duration.ofDays(2)), 4, null);
		steps.apply(joined, me, "CONFIRMED");

		assertThat(storedMeetupId(createdId(create("REVIEW", String.valueOf(hosted))))).isEqualTo(hosted);
		assertThat(storedMeetupId(createdId(create("REVIEW", String.valueOf(joined))))).isEqualTo(joined);
	}

	@Test
	void 자유_글은_예정_모임_지난_모임을_연결하거나_연결하지_않고_쓸_수_있다() throws Exception {
		long upcoming = steps.insertMeetup(other, "참여할 모임", now.plus(Duration.ofDays(2)), 4, null);
		steps.apply(upcoming, me, "CONFIRMED");
		long past = steps.insertMeetup(me, "지난 모임", now.minus(Duration.ofDays(2)), 4, null);

		assertThat(storedMeetupId(createdId(create("FREE", String.valueOf(upcoming))))).isEqualTo(upcoming);
		assertThat(storedMeetupId(createdId(create("FREE", String.valueOf(past))))).isEqualTo(past);
		assertThat(storedMeetupId(createdId(create("FREE", "null")))).isNull();
	}

	@Test
	void 없거나_삭제된_모임을_연결하면_404_MEETUP_NOT_FOUND이고_글은_만들지_않는다() throws Exception {
		long deleted = steps.insertMeetup(me, "지운 모임", now.plus(Duration.ofDays(2)), 4, null);
		steps.deleteMeetup(deleted);
		int before = postCount();

		for (String category : List.of("PROMOTION", "REVIEW", "FREE")) {
			create(category, "999999")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_FOUND"))
					.andExpect(jsonPath("$.error.message").value("없는 모임입니다."));
			create(category, String.valueOf(deleted))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_FOUND"));
		}
		assertThat(postCount()).isEqualTo(before);
	}

	@Test
	void 그_카테고리에_연결할_수_없는_모임은_409_MEETUP_NOT_LINKABLE이고_글은_만들지_않는다() throws Exception {
		Meetups m = prepareMeetups();
		long others = steps.insertMeetup(other, "남의 모임", now.plus(Duration.ofDays(1)), 4, null);
		long pendingOnly = steps.insertMeetup(other, "신청만 한 모임", now.plus(Duration.ofDays(1)), 4, null);
		steps.apply(pendingOnly, me, "PENDING");
		int before = postCount();

		// 모임 홍보: 남의 모임, 참여만 한 모임, 모집 완료(정원 · 마감), 이미 시작한 모임, 취소된 모임
		for (long meetup : List.of(others, m.joinedUpcoming, m.full, m.deadlineClosed, m.pastHosted, m.canceled)) {
			assertNotLinkable("PROMOTION", meetup);
		}
		// 모임 후기: 예정 모임, 남의 모임, 신청만 한 지난 모임, 참여를 취소했거나 내보내진 지난 모임, 취소된 모임
		for (long meetup : List.of(m.soon, others, m.pendingPast, m.canceledApplication, m.kickedPast, m.canceled)) {
			assertNotLinkable("REVIEW", meetup);
		}
		// 자유: 남의 모임, 신청만 한 모임, 참여를 취소했거나 내보내진 모임, 취소된 모임
		for (long meetup : List.of(others, pendingOnly, m.pendingPast, m.canceledApplication, m.kickedPast,
				m.canceled)) {
			assertNotLinkable("FREE", meetup);
		}
		assertThat(postCount()).isEqualTo(before);
	}

	@Test
	void 모임_홍보와_후기에_모임이_없으면_모임을_찾기_전에_400이다() throws Exception {
		create("PROMOTION", "null")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("meetupId"));
		create("REVIEW", "null")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("meetupId"));
	}

	// ---------------------------------------------------------------- PATCH /posts/{postId}

	@Test
	void 모임도_카테고리도_그대로면_그사이_모집_완료가_됐어도_고칠_수_있다() throws Exception {
		long meetup = steps.insertMeetup(me, "곧 찰 모임", now.plus(Duration.ofDays(2)), 2, null);
		long postId = createdId(create("PROMOTION", String.valueOf(meetup)));
		steps.apply(meetup, other, "CONFIRMED");
		assertThat(cardStatus(postId)).isEqualTo("CLOSED");

		update(postId, "PROMOTION", "새 제목", String.valueOf(meetup)).andExpect(status().isOk());
		steps.flush();

		assertThat(storedMeetupId(postId)).isEqualTo(meetup);
		assertThat(jdbcTemplate.queryForObject("SELECT title FROM post_version WHERE post_id = ? AND version = 2",
				String.class, postId)).isEqualTo("새 제목");
	}

	@Test
	void 모임을_그대로_두고_카테고리만_바꾸면_그_카테고리에서_다시_검사한다() throws Exception {
		long upcoming = steps.insertMeetup(me, "예정 모임", now.plus(Duration.ofDays(2)), 4, null);
		long postId = createdId(create("FREE", String.valueOf(upcoming)));

		update(postId, "REVIEW", "제목", String.valueOf(upcoming))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_LINKABLE"));
		steps.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT category FROM post WHERE id = ?", String.class, postId))
				.isEqualTo("FREE");

		update(postId, "PROMOTION", "제목", String.valueOf(upcoming)).andExpect(status().isOk());
		steps.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT category FROM post WHERE id = ?", String.class, postId))
				.isEqualTo("PROMOTION");
	}

	@Test
	void 모집_완료가_된_모임의_홍보_글을_자유_글로_바꾸면_자유에서는_연결할_수_있어_200이다() throws Exception {
		long meetup = steps.insertMeetup(me, "찬 모임", now.plus(Duration.ofDays(2)), 2, null);
		long postId = createdId(create("PROMOTION", String.valueOf(meetup)));
		steps.apply(meetup, other, "CONFIRMED");

		update(postId, "FREE", "제목", String.valueOf(meetup)).andExpect(status().isOk());
		update(postId, "PROMOTION", "제목", String.valueOf(meetup))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_LINKABLE"));
	}

	@Test
	void 자유_글의_연결을_null로_풀거나_다른_모임으로_바꿀_수_있다() throws Exception {
		long first = steps.insertMeetup(me, "첫 모임", now.plus(Duration.ofDays(2)), 4, null);
		long second = steps.insertMeetup(me, "둘째 모임", now.plus(Duration.ofDays(3)), 4, null);
		long postId = createdId(create("FREE", String.valueOf(first)));

		update(postId, "FREE", "제목", String.valueOf(second)).andExpect(status().isOk());
		steps.flush();
		assertThat(storedMeetupId(postId)).isEqualTo(second);

		update(postId, "FREE", "제목", "null").andExpect(status().isOk());
		steps.flush();
		assertThat(storedMeetupId(postId)).isNull();
		mockMvc.perform(get("/api/v1/posts/{id}", postId).cookie(session))
				.andExpect(jsonPath("$.data.meetup").value(nullValue()));
	}

	@Test
	void 새로_연결하려는_모임이_없으면_404_연결할_수_없으면_409이고_글은_그대로다() throws Exception {
		long meetup = steps.insertMeetup(me, "내 모임", now.plus(Duration.ofDays(2)), 4, null);
		long others = steps.insertMeetup(other, "남의 모임", now.plus(Duration.ofDays(2)), 4, null);
		long postId = createdId(create("FREE", String.valueOf(meetup)));

		update(postId, "FREE", "바꾼 제목", "999999")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_FOUND"));
		update(postId, "FREE", "바꾼 제목", String.valueOf(others))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_LINKABLE"));
		steps.flush();

		assertThat(storedMeetupId(postId)).isEqualTo(meetup);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_version WHERE post_id = ?", Integer.class,
				postId)).isEqualTo(1);
	}

	@Test
	void 모임_홍보와_후기의_연결을_null로_풀면_400이다() throws Exception {
		long meetup = steps.insertMeetup(me, "내 모임", now.plus(Duration.ofDays(2)), 4, null);
		long postId = createdId(create("PROMOTION", String.valueOf(meetup)));

		update(postId, "PROMOTION", "제목", "null")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("meetupId"));
	}

	// ---------------------------------------------------------------- GET /posts/{postId}

	@Test
	void 글_상세는_연결한_모임을_모임_카드로_주고_모임_목록의_카드와_같다() throws Exception {
		Meetups m = prepareMeetups();
		steps.apply(m.later, other, "CONFIRMED");

		for (long meetup : List.of(m.soon, m.later, m.full, m.deadlineClosed)) {
			long postId = steps.insertPost(me, "FREE", "글", "본문", now);
			steps.linkMeetup(postId, meetup);
			Map<String, Object> card = detailCard(postId);

			assertThat(card).isNotNull();
			assertThat(card.get("id")).isEqualTo((int) meetup);
			assertThat(listCard(meetup)).isEqualTo(card);
		}
		long postId = steps.insertPost(me, "FREE", "글", "본문", now);
		steps.linkMeetup(postId, m.later);
		mockMvc.perform(get("/api/v1/posts/{id}", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetup.title").value("나중에 시작하는 모임"))
				.andExpect(jsonPath("$.data.meetup.capacity").value(4))
				.andExpect(jsonPath("$.data.meetup.confirmedCount").value(2))
				.andExpect(jsonPath("$.data.meetup.recruitStatus").value("OPEN"));
	}

	@Test
	void 글_상세의_카드는_모임이_취소되거나_모집_완료가_되거나_지나면_recruitStatus가_바뀐다() throws Exception {
		long meetup = steps.insertMeetup(me, "바뀌는 모임", now.plus(Duration.ofDays(2)), 2, null);
		long postId = createdId(create("PROMOTION", String.valueOf(meetup)));
		assertThat(cardStatus(postId)).isEqualTo("OPEN");

		steps.apply(meetup, other, "CONFIRMED");
		assertThat(cardStatus(postId)).isEqualTo("CLOSED");

		clock.setNow(now.plus(Duration.ofDays(3)));
		assertThat(cardStatus(postId)).isEqualTo("PAST");

		clock.setNow(now);
		steps.cancelMeetup(meetup);
		assertThat(cardStatus(postId)).isEqualTo("CANCELED");
	}

	@Test
	void 글_상세는_연결하지_않았거나_모임이_삭제됐으면_meetup이_null이고_글은_200이다() throws Exception {
		long unlinked = steps.insertPost(me, "FREE", "연결 없는 글", "본문", now);
		long meetup = steps.insertMeetup(me, "지울 모임", now.plus(Duration.ofDays(2)), 4, null);
		long linked = createdId(create("PROMOTION", String.valueOf(meetup)));

		mockMvc.perform(get("/api/v1/posts/{id}", unlinked).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetup").value(nullValue()));
		mockMvc.perform(get("/api/v1/posts/{id}", linked).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetup.id").value(meetup));

		steps.deleteMeetup(meetup);
		mockMvc.perform(get("/api/v1/posts/{id}", linked).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(linked))
				.andExpect(jsonPath("$.data.meetup").value(nullValue()));
	}

	// ---------------------------------------------------------------- 준비

	/**
	 * 목록 테스트가 함께 쓰는 모임. 시작 시각은 지금에서 떨어진 시간으로 서로 다르게 둔다.
	 *
	 * <p>내가 연 모임: {@code soon}(+24시간), {@code full}(+36시간, 정원 2에 1명 확정), {@code later}(+48시간),
	 * {@code deadlineClosed}(+60시간, 모집 마감이 지남), {@code startsNow}(지금 시작), {@code pastHosted}(-24시간).
	 * 참여가 확정된 남의 모임: {@code joinedUpcoming}(+72시간), {@code pastJoined}(-72시간).
	 * 어느 목록에도 없어야 하는 모임: 취소 · 삭제 · 남의 모임 · 신청 대기 · 참여를 취소한 모임.
	 */
	private Meetups prepareMeetups() {
		long soon = steps.insertMeetup(me, "곧 시작하는 모임", now.plus(Duration.ofHours(24)), 4, null);
		long full = steps.insertMeetup(me, "정원이 찬 모임", now.plus(Duration.ofHours(36)), 2, null);
		steps.apply(full, other, "CONFIRMED");
		long later = steps.insertMeetup(me, "나중에 시작하는 모임", now.plus(Duration.ofHours(48)), 4, null);
		long deadlineClosed = steps.insertMeetup(me, "마감이 지난 모임", now.plus(Duration.ofHours(60)), 4,
				now.minus(Duration.ofHours(1)));
		long startsNow = steps.insertMeetup(me, "지금 시작한 모임", now, 4, null);
		long pastHosted = steps.insertMeetup(me, "지난 내 모임", now.minus(Duration.ofHours(24)), 4, null);
		long joinedUpcoming = steps.insertMeetup(other, "참여할 모임", now.plus(Duration.ofHours(72)), 4, null);
		steps.apply(joinedUpcoming, me, "CONFIRMED");
		long pastJoined = steps.insertMeetup(other, "다녀온 모임", now.minus(Duration.ofHours(72)), 4, null);
		steps.apply(pastJoined, me, "CONFIRMED");

		long canceled = steps.insertMeetup(me, "취소한 모임", now.plus(Duration.ofHours(30)), 4, null);
		steps.cancelMeetup(canceled);
		long canceledPast = steps.insertMeetup(me, "취소한 지난 모임", now.minus(Duration.ofHours(30)), 4, null);
		steps.cancelMeetup(canceledPast);
		long deleted = steps.insertMeetup(me, "지운 모임", now.plus(Duration.ofHours(20)), 4, null);
		steps.deleteMeetup(deleted);
		long deletedPast = steps.insertMeetup(me, "지운 지난 모임", now.minus(Duration.ofHours(20)), 4, null);
		steps.deleteMeetup(deletedPast);
		long others = steps.insertMeetup(other, "남의 모임", now.plus(Duration.ofHours(10)), 4, null);
		long othersPast = steps.insertMeetup(other, "남의 지난 모임", now.minus(Duration.ofHours(10)), 4, null);
		long pendingPast = steps.insertMeetup(other, "신청만 한 지난 모임", now.minus(Duration.ofHours(40)), 4, null);
		steps.apply(pendingPast, me, "PENDING");
		long canceledApplication = steps.insertMeetup(other, "참여를 취소한 지난 모임", now.minus(Duration.ofHours(50)), 4,
				null);
		steps.apply(canceledApplication, me, "CANCELED");
		long kickedPast = steps.insertMeetup(other, "내보내진 지난 모임", now.minus(Duration.ofHours(60)), 4, null);
		steps.apply(kickedPast, me, "KICKED");
		return new Meetups(soon, full, later, deadlineClosed, startsNow, pastHosted, joinedUpcoming, pastJoined,
				canceled, pendingPast, canceledApplication, kickedPast);
	}

	private record Meetups(long soon, long full, long later, long deadlineClosed, long startsNow, long pastHosted,
			long joinedUpcoming, long pastJoined, long canceled, long pendingPast, long canceledApplication,
			long kickedPast) {
	}

	private static int id(long meetupId) {
		return (int) meetupId;
	}

	private ResultActions linkable(String postCategory) throws Exception {
		return mockMvc.perform(get(LINKABLE).param("postCategory", postCategory).cookie(session));
	}

	private ResultActions create(String category, String meetupId) throws Exception {
		return mockMvc.perform(post("/api/v1/posts").cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content(body(category, "제목", meetupId)));
	}

	private ResultActions update(long postId, String category, String title, String meetupId) throws Exception {
		return mockMvc.perform(patch("/api/v1/posts/{id}", postId).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(category, title, meetupId)));
	}

	private void assertNotLinkable(String category, long meetupId) throws Exception {
		create(category, String.valueOf(meetupId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_LINKABLE"))
				.andExpect(jsonPath("$.error.message").value("이 글에 연결할 수 없는 모임입니다."));
	}

	private long createdId(ResultActions result) throws Exception {
		String response = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.postId")).longValue();
	}

	private Long storedMeetupId(long postId) {
		steps.flush();
		return jdbcTemplate.queryForObject("SELECT meetup_id FROM post WHERE id = ?", Long.class, postId);
	}

	private int postCount() {
		steps.flush();
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Integer.class);
	}

	private String cardStatus(long postId) throws Exception {
		return (String) detailCard(postId).get("recruitStatus");
	}

	private Map<String, Object> detailCard(long postId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/posts/{id}", postId).cookie(session))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.meetup");
	}

	/** 모임 목록(07 모임)이 같은 모임에 주는 카드. */
	private Map<String, Object> listCard(long meetupId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/meetups").param("size", "24").cookie(session))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> found = JsonPath.read(response, "$.data.items[?(@.id == %d)]".formatted(meetupId));
		assertThat(found).hasSize(1);
		return found.getFirst();
	}

	private static String body(String category, String title, String meetupId) {
		return """
				{
				  "category": "%s",
				  "title": "%s",
				  "content": "본문",
				  "interestCategoryIds": [],
				  "imageIds": [],
				  "meetupId": %s
				}
				""".formatted(category, title, meetupId);
	}

}
