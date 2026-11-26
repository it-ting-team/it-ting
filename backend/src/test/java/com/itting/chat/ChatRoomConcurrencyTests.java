package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.itting.chat.domain.ChatErrorCode;
import com.itting.chat.dto.ChatMessageResponse;
import com.itting.chat.dto.ChatMessageSendRequest;
import com.itting.chat.service.ChatMessageService;
import com.itting.chat.service.ChatRoomService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
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
 * 대화방의 일이 동시에 들어올 때. 실제 MySQL의 잠금으로 겨루게 하려고 테스트 트랜잭션을 쓰지 않고 커밋한다.
 *
 * <ul>
 * <li>1:1 대화방에서 한 사람의 나가기와 상대의 보내기가 동시에 와도 "나갔습니다" 뒤에 메시지가 들어가지 않는다
 * (보내기는 두 사람의 {@code user} 줄을 함께 읽기 잠금으로, 나가기는 쓰기 잠금으로 잡는다. {@code docs/db/chat.md}).
 * <li>같은 사진을 두 메시지에 동시에 붙여도 한 메시지에만 붙는다({@code chat_message_image.image_id} UNIQUE).
 * <li>같은 사람의 나가기가 동시에 두 번 와도 안내 메시지는 하나다.
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ChatRoomConcurrencyTests {

	private static final int ROUNDS = 15;

	@Autowired
	ChatRoomService chatRoomService;

	@Autowired
	ChatMessageService chatMessageService;

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
		a = steps.createMember("room-race-a@example.com", "방경주가");
		b = steps.createMember("room-race-b@example.com", "방경주나");
	}

	@AfterEach
	void cleanUp() {
		chat.deleteAllOf(a, b);
	}

	@Test
	void 나가기와_상대의_보내기가_동시에_와도_나갔다는_안내_뒤에_메시지가_없다() throws Exception {
		int sent = 0;
		int blocked = 0;
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);
			long room = chat.insertDirectRoom(a, b, Instant.now());

			List<Outcome> outcomes = race(() -> {
				chatRoomService.leave(a, room);
				return "left";
			}, () -> chatMessageService.send(b, room, new ChatMessageSendRequest("아직 계세요?", List.of())));

			// 나가기는 늘 성공한다.
			assertThat(outcomes.get(0).error()).as("%d번째 겨루기의 나가기", round).isNull();
			List<Map<String, Object>> messages = chat.messages(room);
			assertThat(messages.getLast()).as("%d번째 겨루기의 마지막 메시지", round).containsEntry("type", "LEFT");
			if (outcomes.get(1).error() == null) {
				// 보내기가 먼저 끝났다: 메시지가 안내 앞에 있다.
				sent++;
				assertThat(messages).extracting(message -> message.get("type")).containsExactly("TEXT", "LEFT");
			}
			else {
				// 나가기가 먼저 끝났다: 읽기 전용이라 보낼 수 없고 메시지도 없다.
				blocked++;
				assertThat(errorCodes(outcomes)).containsExactly(ChatErrorCode.CHAT_ROOM_READ_ONLY);
				assertThat(messages).hasSize(1);
			}
			// 나간 사람에게 들어갈 수 없는 방의 안 읽은 새 메시지 알림이 남지 않는다.
			assertThat(jdbcTemplate.queryForObject("""
					SELECT COUNT(*) FROM notification
					WHERE user_id = ? AND type = 'CHAT_MESSAGE_RECEIVED' AND read_at IS NULL
					""", Integer.class, a)).as("%d번째 겨루기의 안 읽은 알림", round).isZero();
		}
		assertThat(sent + blocked).isEqualTo(ROUNDS);
	}

	@Test
	void 같은_사진을_두_메시지에_동시에_붙이면_한_메시지에만_붙는다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);
			long room = chat.insertDirectRoom(a, b, Instant.now());
			long image = steps.uploadImage(a, "https://cdn.example.com/images/race-" + round + ".webp");
			ChatMessageSendRequest request = new ChatMessageSendRequest(null, List.of(image));

			List<Outcome> outcomes = race(() -> chatMessageService.send(a, room, request),
					() -> chatMessageService.send(a, room, request));

			assertThat(successes(outcomes)).as("%d번째 겨루기의 성공", round).hasSize(1);
			assertThat(errorCodes(outcomes)).containsExactly(CommonErrorCode.INVALID_INPUT);
			ChatMessageResponse message = (ChatMessageResponse) successes(outcomes).getFirst();
			// 실패한 쪽의 메시지는 되돌아가 남지 않는다.
			assertThat(chat.messages(room)).hasSize(1);
			assertThat(chat.imageIdsOf(message.id())).containsExactly(image);
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_message_image WHERE image_id = ?",
					Integer.class, image)).isEqualTo(1);
		}
	}

	@Test
	void 같은_사람의_나가기가_동시에_두_번_와도_안내_메시지는_하나다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			chat.deleteChatOf(a, b);
			long room = chat.insertDirectRoom(a, b, Instant.now());

			List<Outcome> outcomes = race(() -> {
				chatRoomService.leave(a, room);
				return "left";
			}, () -> {
				chatRoomService.leave(a, room);
				return "left";
			});

			assertThat(successes(outcomes)).as("%d번째 겨루기의 성공", round).hasSize(1);
			assertThat(errorCodes(outcomes)).containsExactly(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
			assertThat(chat.messages(room)).hasSize(1);
			assertThat(chat.messages(room).getFirst()).containsEntry("type", "LEFT");
		}
	}

	private record Outcome(Object value, Throwable error) {
	}

	private static List<Object> successes(List<Outcome> outcomes) {
		return outcomes.stream().filter(outcome -> outcome.error() == null).map(Outcome::value).toList();
	}

	/** 실패한 쪽의 에러 코드. {@link BusinessException}이 아닌 실패(교착, 예상하지 못한 예외)는 그대로 던져 테스트를 실패시킨다. */
	private static List<Object> errorCodes(List<Outcome> outcomes) {
		List<Object> codes = new ArrayList<>();
		for (Outcome outcome : outcomes) {
			if (outcome.error() == null) {
				continue;
			}
			if (!(outcome.error() instanceof BusinessException business)) {
				throw new AssertionError("예상하지 못한 실패", outcome.error());
			}
			codes.add(business.getErrorCode());
		}
		return codes;
	}

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
