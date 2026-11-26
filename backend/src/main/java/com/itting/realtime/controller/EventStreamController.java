package com.itting.realtime.controller;

import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.realtime.service.EventStreamConnections;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 소식 연결({@code docs/api/06-chat.md}의 {@code GET /api/v1/me/event-stream}, {@code docs/api-convention.md}의
 * "실시간 소식"). 채팅과 알림이 이 연결 하나를 함께 쓴다.
 *
 * <p>응답 틀({@code ApiResult})을 쓰지 않는 예외다. 연결을 열어 두고 소식을 한 건씩 흘려보낸다. 연결을 열기 전의 실패
 * ({@code 401}, {@code 403})는 다른 API와 같은 응답 틀로 나간다.
 */
@Tag(name = "06 채팅", description = "대화 요청 · 대화방 · 메시지 · 읽음 · 나가기 · 공지")
@RestController
public class EventStreamController {

	private final EventStreamConnections connections;

	public EventStreamController(EventStreamConnections connections) {
		this.connections = connections;
	}

	@Operation(summary = "실시간 소식 연결",
			description = "이 계정에 생긴 일을 실시간으로 받는 연결(SSE)이다. 로그인한 뒤 한 번 연결해 계속 열어 둔다. 소식은 "
					+ "CHAT_MESSAGE_CREATED, CHAT_REQUEST_COUNT_CHANGED, NOTIFICATION_UNREAD_CHANGED 셋이다. 연결을 "
					+ "유지하는 신호로 주석 줄(:ping)이 25초마다 온다. 끊긴 동안의 소식은 다시 보내지 않는다. 로그아웃하면 연결이 "
					+ "끊긴다. 응답이 끝나지 않으므로 Swagger UI에서는 결과가 보이지 않는다(curl -N으로 확인한다). 로그인이 "
					+ "필요하고, 새 학기를 고르기 전에도 부를 수 있다.")
	@ApiResponse(responseCode = "200", description = "연결됨. 소식이 한 건씩 흘러온다",
			content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
					schema = @Schema(type = "string",
							example = "event: CHAT_REQUEST_COUNT_CHANGED\ndata: {\"receivedCount\":3}\n\n")))
	@AllowBeforeNewTerm
	@GetMapping(value = "/api/v1/me/event-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter open(@Parameter(hidden = true) @AuthenticationPrincipal Long userId, HttpServletRequest request,
			HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
		}
		// 앞단 프록시가 응답을 모아 두지 않고 바로 흘려보내게 한다(Nginx).
		response.setHeader("X-Accel-Buffering", "no");
		return connections.open(userId, session.getId());
	}

}
