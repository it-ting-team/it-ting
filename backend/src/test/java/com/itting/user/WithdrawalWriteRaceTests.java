package com.itting.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.itting.community.service.CommentService;
import com.itting.community.service.PostReactionService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.global.error.ErrorCode;
import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.dto.MeetupCreateRequest;
import com.itting.meetup.service.MeetupApplicationService;
import com.itting.meetup.service.MeetupLikeService;
import com.itting.meetup.service.MeetupService;
import com.itting.onboarding.dto.OnboardingCompleteRequest;
import com.itting.onboarding.service.OnboardingService;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.dto.MoleUpdateRequest;
import com.itting.user.dto.ProfileUpdateRequest;
import com.itting.user.dto.WithdrawRequest;
import com.itting.user.service.MyProfileService;
import com.itting.user.service.WithdrawalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 탈퇴하는 사이 같은 계정이 보낸 쓰기 요청이 탈퇴 정리에서 빠지지 않는지({@code docs/work/KAN-74.md}). 각 요청은 내 계정 줄을 공유
 * 잠금으로 먼저 잡으므로, 탈퇴가 먼저면 탈퇴가 끝난 뒤 {@code UNAUTHENTICATED}로 거절되고, 요청이 먼저면 탈퇴가 그 줄까지 정리한다.
 * 두 트랜잭션이 실제로 겹쳐 커밋되어야 하므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은 끝에 지운다. 경합은 한 번으로는 우연히 지나갈
 * 수 있어 여러 번 겨룬다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WithdrawalWriteRaceTests {

	private static final int ROUNDS = 10;

	private static final String PASSWORD = CommunitySteps.PASSWORD;

	@Autowired
	WithdrawalService withdrawalService;

	@Autowired
	MeetupService meetupService;

	@Autowired
	MeetupApplicationService applicationService;

	@Autowired
	MeetupLikeService likeService;

	@Autowired
	PostReactionService postReactionService;

	@Autowired
	CommentService commentService;

	@Autowired
	MyProfileService myProfileService;

	@Autowired
	OnboardingService onboardingService;

	@Autowired
	CommunitySteps community;

	@Autowired
	ChatSteps chat;

	@Autowired
	OnboardingSteps onboarding;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	/** 이 테스트가 만든 계정. 끝에 모두 지운다. */
	private final List<Long> created = new ArrayList<>();

	/** 이 테스트가 만든 글. 끝에 모두 지운다. */
	private final List<Long> posts = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (Long postId : posts) {
			jdbcTemplate.update("DELETE FROM comment_like WHERE comment_id IN (SELECT id FROM comment WHERE post_id = ?)",
					postId);
			jdbcTemplate.update("DELETE FROM comment_version WHERE comment_id IN (SELECT id FROM comment WHERE post_id = ?)",
					postId);
			jdbcTemplate.update("DELETE FROM comment WHERE post_id = ?", postId);
			jdbcTemplate.update("DELETE FROM post_like WHERE post_id = ?", postId);
			jdbcTemplate.update("DELETE FROM post_bookmark WHERE post_id = ?", postId);
			jdbcTemplate.update("DELETE FROM post_version WHERE post_id = ?", postId);
			jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
		}
		posts.clear();
		if (created.isEmpty()) {
			return;
		}
		long[] ids = created.stream().mapToLong(Long::longValue).toArray();
		String in = created.stream().map(String::valueOf).collect(Collectors.joining(","));
		chat.deleteChatOf(ids);
		jdbcTemplate.update("DELETE FROM meetup_application WHERE user_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM meetup_like WHERE user_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM meetup_application WHERE meetup_id IN (SELECT id FROM meetup WHERE host_id IN ("
				+ in + "))");
		jdbcTemplate.update("DELETE FROM meetup_like WHERE meetup_id IN (SELECT id FROM meetup WHERE host_id IN ("
				+ in + "))");
		jdbcTemplate.update("DELETE FROM meetup WHERE host_id IN (" + in + ")");
		jdbcTemplate.update("DELETE e FROM burrow_entrance e JOIN burrow b ON b.id = e.burrow_id WHERE b.user_id IN ("
				+ in + ")");
		jdbcTemplate.update("DELETE FROM burrow WHERE user_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM user_answer WHERE user_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM user_withdrawal WHERE user_id IN (" + in + ")");
		chat.deleteAllOf(ids);
		created.clear();
		onboarding.clearNicknameHolds();
	}

	@Test
	void 탈퇴와_모임_만들기가_겹쳐도_탈퇴한_계정이_연_예정_모임이_남지_않는다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			long leaver = member("race-create-" + round + "@example.com", "만들기" + round);
			// 만들기는 짧아 늘 먼저 잠그기 쉽다. 시작을 조금씩 늦춰 탈퇴가 먼저 잠그는 순서도 나오게 한다.
			long delay = round * 15L;

			List<Outcome> outcomes = race(() -> withdraw(leaver), () -> {
				Thread.sleep(delay);
				return meetupService.create(leaver, meetupRequest());
			});

			// 탈퇴가 먼저면 만들기가 거절되고, 만들기가 먼저면 탈퇴가 새 모임을 보고 거절된다. 둘 다 성공하는 일은 없다.
			assertBusinessErrorsOnly(outcomes);
			Outcome withdrawal = outcomes.get(0);
			Outcome creation = outcomes.get(1);
			if (withdrawal.error() == null) {
				assertThat(errorCode(creation)).as("%d번째 겨루기의 만들기", round).isEqualTo(CommonErrorCode.UNAUTHENTICATED);
				assertThat(status(leaver)).isEqualTo("WITHDRAWN");
				assertThat(count("SELECT COUNT(*) FROM meetup WHERE host_id = ?", leaver)).isZero();
			}
			else {
				assertThat(errorCode(withdrawal)).as("%d번째 겨루기의 탈퇴", round)
						.isEqualTo(UserErrorCode.HOSTING_MEETUP_EXISTS);
				assertThat(creation.error()).isNull();
				assertThat(status(leaver)).isEqualTo("ACTIVE");
				assertThat(count("SELECT COUNT(*) FROM meetup WHERE host_id = ?", leaver)).isEqualTo(1);
			}
		}
	}

	@Test
	void 탈퇴와_참여_신청이_겹쳐도_탈퇴한_계정의_기다리는_신청이_남지_않는다() throws Exception {
		long host = member("race-apply-host@example.com", "신청주최");
		for (int round = 0; round < ROUNDS; round++) {
			long leaver = member("race-apply-" + round + "@example.com", "신청" + round);
			long meetupId = upcomingMeetup(host);

			List<Outcome> outcomes = race(() -> withdraw(leaver), () -> applicationService.apply(meetupId, leaver, null));

			assertBusinessErrorsOnly(outcomes);
			assertThat(outcomes.getFirst().error()).as("%d번째 겨루기의 탈퇴", round).isNull();
			assertThat(status(leaver)).isEqualTo("WITHDRAWN");
			assertThat(count("SELECT COUNT(*) FROM meetup_application WHERE user_id = ? AND status = 'PENDING'", leaver))
					.as("%d번째 겨루기의 신청", round).isZero();
		}
	}

	@Test
	void 탈퇴와_찜_빈자리_알림_켜기가_겹쳐도_탈퇴한_계정의_찜이_남지_않는다() throws Exception {
		long host = member("race-like-host@example.com", "찜주최");
		for (int round = 0; round < ROUNDS; round++) {
			long leaver = member("race-like-" + round + "@example.com", "찜" + round);
			long meetupId = upcomingMeetup(host);

			List<Outcome> outcomes = race(() -> withdraw(leaver), () -> {
				likeService.like(meetupId, leaver);
				likeService.enableAlert(meetupId, leaver);
				return "liked";
			});

			assertBusinessErrorsOnly(outcomes);
			assertThat(outcomes.getFirst().error()).as("%d번째 겨루기의 탈퇴", round).isNull();
			assertThat(status(leaver)).isEqualTo("WITHDRAWN");
			assertThat(count("SELECT COUNT(*) FROM meetup_like WHERE user_id = ?", leaver))
					.as("%d번째 겨루기의 찜", round).isZero();
		}
	}

	@Test
	void 탈퇴와_글_댓글_좋아요_북마크가_겹쳐도_탈퇴한_계정의_줄이_남지_않는다() throws Exception {
		long author = member("race-react-author@example.com", "반응작성");
		long postId = community.insertPost(author, "FREE", "경합 글", "본문", Instant.now());
		posts.add(postId);
		long commentId = community.comment(postId, author, null, "PUBLIC");
		for (int round = 0; round < ROUNDS; round++) {
			long leaver = member("race-react-" + round + "@example.com", "반응" + round);

			List<Outcome> outcomes = race(() -> withdraw(leaver), () -> {
				postReactionService.like(leaver, postId);
				postReactionService.bookmark(leaver, postId);
				commentService.like(leaver, commentId);
				return "reacted";
			});

			assertBusinessErrorsOnly(outcomes);
			assertThat(outcomes.getFirst().error()).as("%d번째 겨루기의 탈퇴", round).isNull();
			assertThat(status(leaver)).isEqualTo("WITHDRAWN");
			assertThat(count("SELECT COUNT(*) FROM post_like WHERE user_id = ?", leaver)).as("%d번째 글 좋아요", round)
					.isZero();
			assertThat(count("SELECT COUNT(*) FROM post_bookmark WHERE user_id = ?", leaver)).as("%d번째 북마크", round)
					.isZero();
			assertThat(count("SELECT COUNT(*) FROM comment_like WHERE user_id = ?", leaver)).as("%d번째 댓글 좋아요", round)
					.isZero();
		}
	}

	@Test
	void 탈퇴와_프로필_저장이_겹쳐도_관심사_답변이_남지_않고_서버_오류가_나지_않는다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			long leaver = onboarded("race-profile-" + round + "@example.com", "프로필" + round);
			ProfileUpdateRequest request = new ProfileUpdateRequest("프로필새" + round, testUsers.seoulFloorId(2),
					onboarding.interestIds(4), answers("탈퇴하는 사이 저장하는 답변"), Optional.of("자기소개"));

			List<Outcome> outcomes = race(() -> withdraw(leaver), () -> myProfileService.changeProfile(leaver, request));

			// 탈퇴가 먼저 프로필을 지웠어도 프로필이 없는 오류(500)가 아니라 UNAUTHENTICATED다.
			assertBusinessErrorsOnly(outcomes);
			assertThat(outcomes.getFirst().error()).as("%d번째 겨루기의 탈퇴", round).isNull();
			if (outcomes.get(1).error() != null) {
				assertThat(errorCode(outcomes.get(1))).isEqualTo(CommonErrorCode.UNAUTHENTICATED);
			}
			assertThat(status(leaver)).isEqualTo("WITHDRAWN");
			assertThat(count("SELECT COUNT(*) FROM user_interest WHERE user_id = ?", leaver)).as("%d번째 관심사", round)
					.isZero();
			assertThat(count("SELECT COUNT(*) FROM user_answer WHERE user_id = ?", leaver)).as("%d번째 답변", round)
					.isZero();
		}
	}

	@Test
	void 탈퇴한_계정이_세션만_남아_부르면_모두_UNAUTHENTICATED다() {
		long host = member("withdrawn-call-host@example.com", "남은주최");
		long meetupId = upcomingMeetup(host);
		long postId = community.insertPost(host, "FREE", "남은 글", "본문", Instant.now());
		posts.add(postId);
		long commentId = community.comment(postId, host, null, "PUBLIC");
		long leaver = onboarded("withdrawn-call@example.com", "남은세션");
		withdraw(leaver);
		ProfileUpdateRequest profile = new ProfileUpdateRequest("남은세션새", testUsers.seoulFloorId(2),
				onboarding.interestIds(3), answers("탈퇴한 뒤에 저장하려는 답변"), Optional.empty());
		MoleUpdateRequest mole = new MoleUpdateRequest(Optional.of("HAT_BLUE"), Optional.empty(), Optional.empty(),
				Optional.empty());

		List<Callable<?>> calls = List.of(
				() -> meetupService.create(leaver, meetupRequest()),
				() -> applicationService.apply(meetupId, leaver, null),
				() -> {
					likeService.like(meetupId, leaver);
					return null;
				},
				() -> {
					likeService.enableAlert(meetupId, leaver);
					return null;
				},
				() -> postReactionService.like(leaver, postId),
				() -> postReactionService.bookmark(leaver, postId),
				() -> commentService.like(leaver, commentId),
				() -> myProfileService.changeProfile(leaver, profile),
				() -> myProfileService.changeMole(leaver, mole));
		for (Callable<?> call : calls) {
			assertThatThrownBy(call::call).isInstanceOfSatisfying(BusinessException.class,
					error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
		}
		assertThat(count("SELECT COUNT(*) FROM meetup WHERE host_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM meetup_application WHERE user_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM meetup_like WHERE user_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM post_like WHERE user_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM post_bookmark WHERE user_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM comment_like WHERE user_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM user_interest WHERE user_id = ?", leaver)).isZero();
	}

	// ---- 도구 ----

	private long member(String email, String nickname) {
		long userId = community.createMember(email, nickname);
		created.add(userId);
		return userId;
	}

	/** 15기 · 서울 2층으로 온보딩을 마친 계정. 땅굴 · 관심사 · 답변이 있다. */
	private long onboarded(String email, String nickname) {
		long userId = testUsers.createEmailUser(email, PASSWORD);
		created.add(userId);
		onboardingService.complete(userId, new OnboardingCompleteRequest(nickname,
				new OnboardingCompleteRequest.Mole(null, null, null, null), onboarding.interestIds(3),
				onboarding.questionIds(3).stream()
						.map(id -> new OnboardingCompleteRequest.Answer(id, "온보딩에서 저장한 답변입니다"))
						.toList()));
		return userId;
	}

	private List<ProfileUpdateRequest.Answer> answers(String answer) {
		return onboarding.questionIds(3).stream().map(id -> new ProfileUpdateRequest.Answer(id, answer)).toList();
	}

	/** 사흘 뒤에 시작하는 정원 4명의 모집 중인 모임. */
	private long upcomingMeetup(long hostId) {
		return community.insertMeetup(hostId, "경합 모임", Instant.now().plus(Duration.ofDays(3)), 4, null);
	}

	private static MeetupCreateRequest meetupRequest() {
		return new MeetupCreateRequest("경합 모임", MeetupCategory.STUDY, null, 4, null, false,
				Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MICROS), null, MeetupMode.ONLINE, null,
				null, null, null);
	}

	/** 세션이 없는 요청으로 비밀번호 계정이 탈퇴한다. */
	private Object withdraw(long userId) {
		withdrawalService.withdraw(userId, new WithdrawRequest(PASSWORD, null), new MockHttpServletRequest());
		return "withdrawn";
	}

	private String status(long userId) {
		return jdbcTemplate.queryForObject("SELECT status FROM `user` WHERE id = ?", String.class, userId);
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private static ErrorCode errorCode(Outcome outcome) {
		assertThat(outcome.error()).isInstanceOf(BusinessException.class);
		return ((BusinessException) outcome.error()).getErrorCode();
	}

	/** 한 일의 결과. 값이거나 던진 예외다. */
	private record Outcome(Object value, Throwable error) {
	}

	/** {@link BusinessException}이 아닌 예외(교착 상태, 프로필이 없는 오류 같은 것)는 테스트를 실패시킨다. */
	private static void assertBusinessErrorsOnly(List<Outcome> outcomes) {
		for (Outcome outcome : outcomes) {
			if (outcome.error() != null && !(outcome.error() instanceof BusinessException)) {
				throw new AssertionError("업무 오류가 아닌 예외가 났습니다.", outcome.error());
			}
		}
	}

	/** 두 일을 두 스레드가 동시에 시작해 둘 다 끝날 때까지 기다리고, 일마다 결과를 모은다(넘긴 순서대로). */
	private static List<Outcome> race(Callable<?>... tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
		try {
			CyclicBarrier start = new CyclicBarrier(tasks.length);
			List<Future<Outcome>> futures = new ArrayList<>();
			for (Callable<?> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await(10, TimeUnit.SECONDS);
					try {
						return new Outcome(task.call(), null);
					}
					catch (Throwable error) {
						return new Outcome(null, error);
					}
				}));
			}
			List<Outcome> outcomes = new ArrayList<>();
			for (Future<Outcome> future : futures) {
				outcomes.add(future.get(60, TimeUnit.SECONDS));
			}
			return outcomes;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
