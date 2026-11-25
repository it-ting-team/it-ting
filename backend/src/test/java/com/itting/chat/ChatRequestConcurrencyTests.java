package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.itting.chat.domain.ChatErrorCode;
import com.itting.chat.dto.ChatRequestAcceptResponse;
import com.itting.chat.dto.ChatRequestCreateRequest;
import com.itting.chat.dto.ChatRequestCreateResponse;
import com.itting.chat.service.ChatRequestService;
import com.itting.global.error.BusinessException;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 두 사람의 일이 동시에 들어와도 요청은 하나, 대화방은 하나이고 오류 없이 끝난다({@code docs/db/chat.md}의 같은 두 사람을
 * 한 번에 하나씩 처리하는 규칙). 두 트랜잭션이 실제로 겹쳐 커밋되어야 하므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은 끝에 지운다.
 * 경합은 한 번으로는 우연히 지나갈 수 있어 여러 번 겨룬다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ChatRequestConcurrencyTests {

	private static final int ROUNDS = 15;

	@Autowired
	ChatRequestService chatRequestService;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	JdbcTemplate jdbcTemplate;

	long a;

	long b;

	@BeforeEach
	void setUp() {
		a = steps.createMember("race-a@example.com", "경주가");
		b = steps.createMember("race-b@example.com", "경주나");
	}

	@AfterEach
	void cleanUp() {
		chat.deleteAllOf(a, b);
	}

	@Test
	void 서로에게_동시에_보내면_요청은_하나이고_하나는_ALREADY_RECEIVED로_거절된다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);

			List<Outcome> outcomes = race(
					() -> chatRequestService.send(a, new ChatRequestCreateRequest(b, "가가 보냄")),
					() -> chatRequestService.send(b, new ChatRequestCreateRequest(a, "나가 보냄")));

			assertThat(successes(outcomes)).as("%d번째 겨루기의 성공", round).hasSize(1);
			assertThat(errorCodes(outcomes)).containsExactly(ChatErrorCode.CHAT_REQUEST_ALREADY_RECEIVED);
			assertThat(chat.requestsBetween(a, b)).hasSize(1);
			assertThat(chat.requestsBetween(a, b).getFirst()).containsEntry("status", "PENDING");
			assertThat(chat.roomCount()).isZero();
			// 알림도 하나뿐이다. 받은 사람에게만 간다.
			assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM notification WHERE type = 'CHAT_REQUEST_RECEIVED' AND user_id IN (?, ?)",
					Integer.class, a, b)).isEqualTo(1);
		}
	}

	@Test
	void 같은_사람에게_동시에_두_번_보내면_요청은_하나이고_하나는_ALREADY_SENT로_거절된다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);

			List<Outcome> outcomes = race(
					() -> chatRequestService.send(a, new ChatRequestCreateRequest(b, "첫 번째")),
					() -> chatRequestService.send(a, new ChatRequestCreateRequest(b, "두 번째")));

			assertThat(successes(outcomes)).as("%d번째 겨루기의 성공", round).hasSize(1);
			assertThat(errorCodes(outcomes)).containsExactly(ChatErrorCode.CHAT_REQUEST_ALREADY_SENT);
			assertThat(chat.requestsBetween(a, b)).hasSize(1);
			assertThat(chat.notificationTypes(b)).containsExactly("CHAT_REQUEST_RECEIVED");
		}
	}

	@Test
	void 같은_요청을_동시에_두_번_수락하면_대화방은_하나이고_둘_다_같은_방_id를_받는다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);
			long request = chat.insertRequest(a, b, "인사", "PENDING", Instant.now().minus(Duration.ofHours(1)), null);

			List<Outcome> outcomes = race(() -> chatRequestService.accept(b, request),
					() -> chatRequestService.accept(b, request));

			assertThat(errorCodes(outcomes)).as("%d번째 겨루기의 오류", round).isEmpty();
			List<Long> roomIds = successes(outcomes).stream()
					.map(value -> ((ChatRequestAcceptResponse) value).chatRoomId())
					.toList();
			assertThat(roomIds).hasSize(2).containsOnly(roomIds.getFirst());
			assertThat(chat.roomCount()).isEqualTo(1);
			assertThat(chat.members(roomIds.getFirst())).hasSize(2);
			assertThat(chat.messages(roomIds.getFirst())).hasSize(1);
			assertThat(chat.requestStatus(request)).isEqualTo("ACCEPTED");
			// 수락 알림도 한 번이다.
			assertThat(chat.notificationTypes(a)).containsExactly("CHAT_REQUEST_ACCEPTED");
		}
	}

	@Test
	void 수락과_취소가_동시에_와도_하나만_이긴다() throws Exception {
		int accepted = 0;
		int canceled = 0;
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);
			long request = chat.insertRequest(a, b, null, "PENDING", Instant.now().minus(Duration.ofHours(1)), null);

			List<Outcome> outcomes = race(() -> chatRequestService.accept(b, request), () -> {
				chatRequestService.cancel(a, request);
				return "canceled";
			});

			String status = chat.requestStatus(request);
			if (status.equals("ACCEPTED")) {
				accepted++;
				// 수락이 이겼다. 취소는 409와 대화방 id를 받는다.
				assertThat(chat.roomCount()).isEqualTo(1);
				assertThat(errorCodes(outcomes)).containsExactly(ChatErrorCode.CHAT_REQUEST_ALREADY_ACCEPTED);
			}
			else {
				canceled++;
				// 취소가 이겼다. 수락은 404이고 대화방은 없다.
				assertThat(status).isEqualTo("CANCELED");
				assertThat(chat.roomCount()).isZero();
				assertThat(errorCodes(outcomes)).containsExactly(ChatErrorCode.CHAT_REQUEST_NOT_FOUND);
				assertThat(chat.notifications(a)).isEmpty();
			}
		}
		assertThat(accepted + canceled).isEqualTo(ROUNDS);
	}

	@Test
	void 받은_요청에_수락하는_사이_내가_그_사람에게_보내도_대화방은_하나이고_새_요청은_없다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);
			long request = chat.insertRequest(a, b, null, "PENDING", Instant.now().minus(Duration.ofHours(1)), null);

			List<Outcome> outcomes = race(() -> chatRequestService.accept(b, request),
					() -> chatRequestService.send(b, new ChatRequestCreateRequest(a, "저도요")));

			// 수락이 먼저면 보내기는 CHAT_ROOM_ALREADY_EXISTS, 보내기가 먼저면 CHAT_REQUEST_ALREADY_RECEIVED다.
			assertThat(errorCodes(outcomes)).as("%d번째 겨루기의 오류", round)
					.hasSize(1)
					.isSubsetOf(List.of(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS, ChatErrorCode.CHAT_REQUEST_ALREADY_RECEIVED));
			assertThat(successes(outcomes)).hasSize(1);
			assertThat(chat.roomCount()).isEqualTo(1);
			assertThat(chat.requestsBetween(a, b)).hasSize(1);
			assertThat(chat.requestStatus(request)).isEqualTo("ACCEPTED");
		}
	}

	@Test
	void 내가_거절한_요청이_남은_사람에게_동시에_두_번_보내도_대화방은_하나이다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);
			long request = chat.insertRequest(a, b, "인사", "REJECTED", Instant.now().minus(Duration.ofHours(1)), null);

			List<Outcome> outcomes = race(
					() -> chatRequestService.send(b, new ChatRequestCreateRequest(a, "생각이 바뀌었어요")),
					() -> chatRequestService.send(b, new ChatRequestCreateRequest(a, "생각이 바뀌었어요")));

			assertThat(successes(outcomes)).as("%d번째 겨루기의 성공", round).hasSize(1);
			assertThat(((ChatRequestCreateResponse) successes(outcomes).getFirst()).chatRoomId()).isNotNull();
			assertThat(errorCodes(outcomes)).containsExactly(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS);
			assertThat(chat.roomCount()).isEqualTo(1);
			assertThat(chat.requestsBetween(a, b)).hasSize(1);
			assertThat(chat.requestStatus(request)).isEqualTo("ACCEPTED");
			assertThat(chat.notificationTypes(a)).containsExactly("CHAT_REQUEST_ACCEPTED", "CHAT_MESSAGE_RECEIVED");
		}
	}

	// ---- 겨루기 도구 ----

	/** 한 일의 결과. 값이거나 던진 예외다. */
	private record Outcome(Object value, Throwable error) {
	}

	/** 성공한 값들. */
	private static List<Object> successes(List<Outcome> outcomes) {
		return outcomes.stream().filter(outcome -> outcome.error() == null).map(Outcome::value).toList();
	}

	/**
	 * 실패한 것들의 에러 코드. {@link BusinessException}이 아닌 예외(교착 상태 같은 것)는 테스트를 실패시킨다.
	 */
	private static List<Object> errorCodes(List<Outcome> outcomes) {
		List<Object> codes = new ArrayList<>();
		for (Outcome outcome : outcomes) {
			if (outcome.error() == null) {
				continue;
			}
			if (outcome.error() instanceof BusinessException exception) {
				codes.add(exception.getErrorCode());
			}
			else {
				throw new AssertionError("업무 오류가 아닌 예외가 났습니다.", outcome.error());
			}
		}
		return codes;
	}

	/** 두 일을 두 스레드가 동시에 시작해 둘 다 끝날 때까지 기다리고, 각각의 결과를 모은다. */
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
