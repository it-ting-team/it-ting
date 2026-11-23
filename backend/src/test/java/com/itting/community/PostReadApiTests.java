package com.itting.community;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.itting.support.CommunitySteps;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글을 읽는 API: 목록, 인기 글과 관심사, 상세, 다음 읽을 글.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class PostReadApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CommunitySteps steps;

	@Autowired
	TestUsers testUsers;

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

	@Test
	void 목록은_공개_글만_최신순으로_글_카드와_페이지를_준다() throws Exception {
		long old = steps.insertPost(other, "FREE", "오래된 글", "본문", ago(3));
		long recent = steps.insertPost(other, "PROMOTION", "새 글", "가".repeat(120), ago(1));
		steps.insertPost(other, "FREE", "PUBLIC", "가려질 글", "본문", ago(2));
		steps.insertPost(other, "FREE", "HIDDEN", "가려진 글", "본문", ago(2));
		steps.insertPost(other, "FREE", "DELETED", "지운 글", "본문", ago(2));
		steps.tag(recent, 2, 1);
		steps.like(recent, me, ago(1));
		steps.bookmark(recent, me);
		long comment = steps.comment(recent, me, null, "PUBLIC");
		steps.comment(recent, other, comment, "HIDDEN");
		steps.comment(recent, other, null, "DELETED");
		steps.attachImage(recent, 1, steps.uploadImage(other, "https://cdn.example/1.webp"));

		mockMvc.perform(get("/api/v1/posts").param("size", "2").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalItems").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.items[*].title").value(contains("새 글", "가려질 글")))
				.andExpect(jsonPath("$.data.items[0].id").value(recent))
				.andExpect(jsonPath("$.data.items[0].category").value("PROMOTION"))
				.andExpect(jsonPath("$.data.items[0].contentPreview").value("가".repeat(100)))
				.andExpect(jsonPath("$.data.items[0].thumbnailUrl").value("https://cdn.example/1.webp"))
				.andExpect(jsonPath("$.data.items[0].author.id").value(other))
				.andExpect(jsonPath("$.data.items[0].author.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.items[0].author.mole.head").value("CAP_RED"))
				.andExpect(jsonPath("$.data.items[0].author.campusName").value("서울"))
				.andExpect(jsonPath("$.data.items[0].author.generation").value(15))
				.andExpect(jsonPath("$.data.items[0].author.floor").value(2))
				.andExpect(jsonPath("$.data.items[0].author.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.items[0].interestCategories[*].id").value(contains(1, 2)))
				.andExpect(jsonPath("$.data.items[0].viewCount").value(0))
				.andExpect(jsonPath("$.data.items[0].likeCount").value(1))
				.andExpect(jsonPath("$.data.items[0].commentCount").value(2))
				.andExpect(jsonPath("$.data.items[0].isBookmarked").value(true))
				.andExpect(jsonPath("$.data.items[0].isHidden").value(false))
				.andExpect(jsonPath("$.data.items[0].isReportedByMe").value(false))
				.andExpect(jsonPath("$.data.items[1].thumbnailUrl").doesNotExist())
				.andExpect(jsonPath("$.data.items[1].interestCategories").value(empty()))
				.andExpect(jsonPath("$.data.items[1].isBookmarked").value(false));

		mockMvc.perform(get("/api/v1/posts").param("size", "2").param("page", "2").cookie(session))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) old)));
		mockMvc.perform(get("/api/v1/posts").param("page", "9").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").value(empty()))
				.andExpect(jsonPath("$.data.totalItems").value(3));
	}

	@Test
	void 카테고리_내_관심사_대분류_검색어를_함께_건다() throws Exception {
		steps.setInterests(me, List.of(steps.firstInterestOf(2)));
		long match = steps.insertPost(other, "FREE", "러닝 같이 해요", "한강에서", ago(1));
		steps.tag(match, 2);
		long otherCategory = steps.insertPost(other, "REVIEW", "러닝 후기", "좋았어요", ago(1));
		steps.tag(otherCategory, 2);
		long notMyInterest = steps.insertPost(other, "FREE", "러닝 음악", "추천", ago(1));
		steps.tag(notMyInterest, 5);
		long noKeyword = steps.insertPost(other, "FREE", "등산 가요", "주말에", ago(1));
		steps.tag(noKeyword, 2);

		mockMvc.perform(get("/api/v1/posts").cookie(session)
						.param("category", "FREE").param("isMyInterestsOnly", "true").param("keyword", " 러닝 "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) match)));
		mockMvc.perform(get("/api/v1/posts").cookie(session).param("interestCategoryId", "5"))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) notMyInterest)));
		mockMvc.perform(get("/api/v1/posts").cookie(session).param("keyword", "   "))
				.andExpect(jsonPath("$.data.totalItems").value(4));
	}

	@Test
	void 검색어는_최신_버전과_닉네임에서_찾고_퍼센트는_글자_그대로_찾는다() throws Exception {
		long edited = steps.insertPost(other, "FREE", "옛 제목", "본문", ago(3));
		steps.addVersion(edited, 2, "새 제목", "본문", ago(2));
		long percent = steps.insertPost(me, "FREE", "할인 50% 정보", "본문", ago(2));
		steps.insertPost(me, "FREE", "할인 500 정보", "본문", ago(1));

		mockMvc.perform(get("/api/v1/posts").cookie(session).param("keyword", "옛 제목"))
				.andExpect(jsonPath("$.data.items").value(empty()));
		mockMvc.perform(get("/api/v1/posts").cookie(session).param("keyword", "새 제목"))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) edited)));
		mockMvc.perform(get("/api/v1/posts").cookie(session).param("keyword", "두더지왕"))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) edited)));
		mockMvc.perform(get("/api/v1/posts").cookie(session).param("keyword", "50%"))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) percent)));
	}

	@Test
	void 인기순은_최근_7일_좋아요로_댓글순은_삭제되지_않은_댓글로_정렬한다() throws Exception {
		long third = steps.createMember("third@example.com", "세번째");
		long a = steps.insertPost(other, "FREE", "A", "본문", ago(5));
		long b = steps.insertPost(other, "FREE", "B", "본문", ago(4));
		long c = steps.insertPost(other, "FREE", "C", "본문", ago(3));
		steps.like(a, me, now.minus(Duration.ofDays(8)));
		steps.like(a, other, now.minus(Duration.ofDays(8)));
		steps.like(b, me, ago(1));
		steps.like(c, third, ago(1));
		steps.comment(a, me, null, "PUBLIC");
		steps.comment(a, me, null, "HIDDEN");
		steps.comment(b, me, null, "PUBLIC");
		steps.comment(c, me, null, "DELETED");
		steps.comment(c, me, null, "DELETED");
		steps.comment(c, me, null, "DELETED");

		mockMvc.perform(get("/api/v1/posts").cookie(session).param("sort", "POPULAR"))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) c, (int) b, (int) a)));
		mockMvc.perform(get("/api/v1/posts").cookie(session).param("sort", "MOST_COMMENTED"))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) a, (int) b, (int) c)));
	}

	@Test
	void 탈퇴한_작성자는_id와_isWithdrawn만_준다() throws Exception {
		steps.insertPost(other, "FREE", "글", "본문", ago(1));
		testUsers.withdraw(other);

		mockMvc.perform(get("/api/v1/posts").cookie(session))
				.andExpect(jsonPath("$.data.items[0].author.id").value(other))
				.andExpect(jsonPath("$.data.items[0].author.isWithdrawn").value(true))
				.andExpect(jsonPath("$.data.items[0].author.nickname").doesNotExist())
				.andExpect(jsonPath("$.data.items[0].author.mole.head").doesNotExist())
				.andExpect(jsonPath("$.data.items[0].author.generation").doesNotExist());
	}

	@Test
	void 목록의_범위를_벗어난_값은_400이다() throws Exception {
		mockMvc.perform(get("/api/v1/posts").cookie(session)
						.param("size", "51").param("page", "0").param("keyword", "가".repeat(51)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(
						containsInAnyOrder("size", "page", "keyword")));
		mockMvc.perform(get("/api/v1/posts").cookie(session).param("sort", "OLDEST"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("sort"));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/posts")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/posts/highlights")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/posts/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/posts/1/related-posts")).andExpect(status().isUnauthorized());
	}

	@Test
	void 인기_글은_0점을_빼고_관심사는_최근_7일_글_수로_8개를_준다() throws Exception {
		long a = steps.insertPost(other, "FREE", "A", "본문", ago(6));
		long b = steps.insertPost(other, "REVIEW", "B", "본문", ago(5));
		long noScore = steps.insertPost(other, "FREE", "점수 없음", "본문", ago(1));
		long hidden = steps.insertPost(other, "FREE", "HIDDEN", "가려짐", "본문", ago(1));
		steps.like(a, me, ago(1));
		steps.like(b, me, ago(1));
		steps.like(b, other, ago(1));
		steps.like(hidden, me, ago(1));
		steps.tag(a, 3);
		steps.tag(b, 3, 4);
		steps.tag(noScore, 4, 12);
		steps.tag(hidden, 1);
		long old = steps.insertPost(other, "FREE", "오래된 글", "본문", now.minus(Duration.ofDays(8)));
		steps.tag(old, 9);
		for (long category = 5; category <= 10; category++) {
			if (category != 9) {
				steps.tag(steps.insertPost(other, "FREE", "태그 " + category, "본문", ago(1)), category);
			}
		}

		mockMvc.perform(get("/api/v1/posts/highlights").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.weeklyTopPosts[*].id").value(contains((int) b, (int) a)))
				.andExpect(jsonPath("$.data.weeklyTopPosts[0].rank").value(1))
				.andExpect(jsonPath("$.data.weeklyTopPosts[0].category").value("REVIEW"))
				.andExpect(jsonPath("$.data.weeklyTopPosts[0].title").value("B"))
				.andExpect(jsonPath("$.data.weeklyTopPosts[1].rank").value(2))
				.andExpect(jsonPath("$.data.trendingInterestCategories[*].id").value(
						contains(3, 4, 5, 6, 7, 8, 10, 12)))
				.andExpect(jsonPath("$.data.trendingInterestCategories[0].name").value("문화생활"));
	}

	@Test
	void 남의_글_상세는_겹치는_관심사와_내_상태를_준다() throws Exception {
		long shared = steps.firstInterestOf(2);
		steps.setInterests(me, List.of(shared, steps.firstInterestOf(4)));
		steps.setInterests(other, List.of(steps.firstInterestOf(1), shared));
		long post = steps.insertPost(other, "FREE", "옛 제목", "옛 본문", ago(2));
		steps.addVersion(post, 2, "새 제목", "새 본문", ago(1));
		steps.tag(post, 2);
		long first = steps.uploadImage(other, "https://cdn.example/1.webp");
		long second = steps.uploadImage(other, "https://cdn.example/2.webp");
		steps.attachImage(post, 2, first);
		steps.attachImage(post, 1, second);
		steps.like(post, me, ago(1));
		steps.comment(post, other, null, "PUBLIC");

		mockMvc.perform(get("/api/v1/posts/{id}", post).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(post))
				.andExpect(jsonPath("$.data.title").value("새 제목"))
				.andExpect(jsonPath("$.data.content").value("새 본문"))
				.andExpect(jsonPath("$.data.images[*].id").value(contains((int) second, (int) first)))
				.andExpect(jsonPath("$.data.images[0].url").value("https://cdn.example/2.webp"))
				.andExpect(jsonPath("$.data.author.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.authorSharedInterests", hasSize(1)))
				.andExpect(jsonPath("$.data.authorSharedInterests[0].id").value(shared))
				.andExpect(jsonPath("$.data.authorSharedInterests[0].interestCategoryId").value(2))
				.andExpect(jsonPath("$.data.authorSharedInterests[0].isShared").value(true))
				.andExpect(jsonPath("$.data.interestCategories[0].name").value("스포츠"))
				.andExpect(jsonPath("$.data.meetup").doesNotExist())
				.andExpect(jsonPath("$.data.isEdited").value(true))
				.andExpect(jsonPath("$.data.likeCount").value(1))
				.andExpect(jsonPath("$.data.commentCount").value(1))
				.andExpect(jsonPath("$.data.isLiked").value(true))
				.andExpect(jsonPath("$.data.isBookmarked").value(false))
				.andExpect(jsonPath("$.data.isMine").value(false))
				.andExpect(jsonPath("$.data.isHidden").value(false))
				.andExpect(jsonPath("$.data.isReportedByMe").value(false))
				.andExpect(jsonPath("$.data.canEdit").value(false));
	}

	@Test
	void 가려진_내_글은_보이고_고칠_수_없다고_알린다() throws Exception {
		steps.setInterests(me, List.of(steps.firstInterestOf(2)));
		long post = steps.insertPost(me, "FREE", "HIDDEN", "내 글", "본문", ago(1));

		mockMvc.perform(get("/api/v1/posts/{id}", post).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isMine").value(true))
				.andExpect(jsonPath("$.data.isHidden").value(true))
				.andExpect(jsonPath("$.data.isEdited").value(false))
				.andExpect(jsonPath("$.data.canEdit").value(false))
				.andExpect(jsonPath("$.data.authorSharedInterests").value(empty()));
	}

	@Test
	void 없거나_삭제된_글은_404_POST_NOT_FOUND_가려진_남의_글은_404_POST_HIDDEN이다() throws Exception {
		long deleted = steps.insertPost(other, "FREE", "DELETED", "지운 글", "본문", ago(1));
		long hidden = steps.insertPost(other, "FREE", "HIDDEN", "가려진 글", "본문", ago(1));

		mockMvc.perform(get("/api/v1/posts/{id}", 999_999).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/posts/{id}", deleted).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/posts/{id}", hidden).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
		mockMvc.perform(get("/api/v1/posts/{id}/related-posts", deleted).cookie(session))
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/posts/{id}/related-posts", hidden).cookie(session))
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
	}

	@Test
	void 다음_읽을_글은_대분류가_겹치는_공개_글을_인기_순으로_3개_준다() throws Exception {
		long current = steps.insertPost(me, "FREE", "지금 글", "본문", ago(9));
		steps.tag(current, 2, 3);
		steps.like(current, other, ago(1));
		long popular = steps.insertPost(other, "FREE", "인기", "본문", ago(8));
		steps.tag(popular, 3);
		steps.like(popular, me, ago(1));
		long newer = steps.insertPost(other, "FREE", "새 글", "본문", ago(2));
		steps.tag(newer, 2);
		long older = steps.insertPost(other, "FREE", "옛 글", "본문", ago(3));
		steps.tag(older, 2, 5);
		long oldest = steps.insertPost(other, "FREE", "가장 옛 글", "본문", ago(4));
		steps.tag(oldest, 2);
		long unrelated = steps.insertPost(other, "FREE", "다른 관심사", "본문", ago(1));
		steps.tag(unrelated, 9);
		steps.like(unrelated, me, ago(1));
		long hidden = steps.insertPost(other, "FREE", "HIDDEN", "가려짐", "본문", ago(1));
		steps.tag(hidden, 2);

		mockMvc.perform(get("/api/v1/posts/{id}/related-posts", current).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) popular, (int) newer, (int) older)))
				.andExpect(jsonPath("$.data.items[0].likeCount").value(1));

		long untagged = steps.insertPost(me, "FREE", "태그 없음", "본문", ago(1));
		mockMvc.perform(get("/api/v1/posts/{id}/related-posts", untagged).cookie(session))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) unrelated, (int) popular,
						(int) current)));
	}

	private Instant ago(int hours) {
		return now.minus(Duration.ofHours(hours));
	}

}
