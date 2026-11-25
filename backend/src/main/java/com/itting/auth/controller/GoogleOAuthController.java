package com.itting.auth.controller;

import java.net.URI;

import com.itting.auth.service.GoogleOAuthCookieService;
import com.itting.auth.service.GoogleOAuthService;
import com.itting.auth.service.SignupCookieService;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.global.security.AllowDuringOnboarding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Google 로그인과 설정의 Google 확인. JSON을 주고받는 API가 아니라 브라우저가 이동하는 주소라서 응답 틀을 쓰지 않고
 * 늘 {@code 302}와 {@code Location}으로 답한다. 실패도 {@code Location}의 {@code error}로 알린다.
 *
 * <p>시작과 콜백은 {@code SecurityConfig.PUBLIC_API}에 있다. {@code verify}는 로그인이 필요하고, 로그인하지 않았으면
 * 로그인 검사가 JSON {@code 401} 대신 로그인 화면으로 보낸다({@code SecurityConfig.GOOGLE_VERIFY}). 시작과 콜백은 로그인 없이 부르는 API라 두 표시를 모두 붙이고,
 * {@code verify}는 설정 영역의 API라 붙이지 않는다(온보딩을 마친 계정만 부른다).
 */
@Tag(name = "02 로그인 · 회원가입", description = "로그인, 로그아웃, 내 계정, 회원가입, Google 로그인")
@RestController
@RequestMapping("/api/v1/oauth/google")
public class GoogleOAuthController {

	private final GoogleOAuthService googleOAuthService;

	public GoogleOAuthController(GoogleOAuthService googleOAuthService) {
		this.googleOAuthService = googleOAuthService;
	}

	@Operation(summary = "Google 로그인 시작",
			description = "브라우저가 이 주소로 이동하면 Google 로그인 화면으로 보낸다(302). 이 로그인 시도를 구분하는 값을 만들어 "
					+ "쿠키 ITTING_OAUTH(HttpOnly, 10분)로 주고 서버에도 10분 동안 기억해 둔다. 응답 틀을 쓰지 않고, 로그인은 필요 없다.")
	@ApiResponse(responseCode = "302", description = "Location: Google 로그인 화면. 돌아올 주소는 /api/v1/oauth/google/callback이다")
	@AllowDuringOnboarding
	@AllowBeforeNewTerm
	@GetMapping
	@ResponseStatus(HttpStatus.FOUND)
	public ResponseEntity<Void> login(HttpServletResponse response) {
		return redirect(googleOAuthService.startLogin(response));
	}

	@Operation(summary = "Google 로그인 콜백",
			description = "Google이 로그인을 마친 브라우저를 돌려보내는 주소다. 프론트엔드가 직접 부르지 않는다. 쿠키 ITTING_OAUTH와 state가 같고 "
					+ "한 번도 쓰지 않은 시도일 때만 받아들인다. 이미 가입한 계정이면 세션 쿠키를 주고, 처음 쓰는 Google 계정이면 가입 쿠키를 주고(계정은 "
					+ "만들지 않는다), 설정에서 시작한 확인이면 확인 기록만 남긴다. 어느 경우든 프론트엔드 주소로 보내고(302) 실패는 error로 알린다. "
					+ "응답 틀을 쓰지 않고, 로그인은 필요 없다.")
	@ApiResponse(responseCode = "302",
			description = "Location: 로그인됨 /, 처음 쓰는 계정 /signup/ssafy?method=google, 로그인 실패 /login?error=<code>"
					+ "(정지면 reason, endsAt도), 연동 확인 /settings/google, 탈퇴 확인 /settings/withdraw")
	@AllowDuringOnboarding
	@AllowBeforeNewTerm
	@GetMapping("/callback")
	@ResponseStatus(HttpStatus.FOUND)
	public ResponseEntity<Void> callback(
			@Parameter(description = "Google이 준 인가 코드") @RequestParam(required = false) String code,
			@Parameter(description = "시작할 때 만든 값. 쿠키 ITTING_OAUTH와 같아야 한다") @RequestParam(required = false) String state,
			@Parameter(description = "Google이 붙인 오류. 사용자가 취소하면 access_denied") @RequestParam(required = false) String error,
			@Parameter(hidden = true) @CookieValue(name = GoogleOAuthCookieService.COOKIE_NAME, required = false) String stateCookie,
			@Parameter(hidden = true) @CookieValue(name = SignupCookieService.COOKIE_NAME, required = false) String signupToken,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			HttpServletRequest request, HttpServletResponse response) {
		return redirect(googleOAuthService.handleCallback(code, state, error, stateCookie, signupToken, userId, request,
				response));
	}

	@Operation(summary = "Google 확인 시작(설정)",
			description = "설정에서 Google 계정을 확인받으러 Google로 보낸다(302). purpose는 LINK(Google 계정 연동)나 WITHDRAW(탈퇴 전 본인 "
					+ "확인)다. 로그인 세션이 없으면 로그인 화면으로, purpose가 틀리면 설정의 첫 화면으로 보낸다. 돌아온 뒤의 일은 콜백이 한다. "
					+ "응답 틀을 쓰지 않는다.")
	@ApiResponse(responseCode = "302", description = "Location: Google 로그인 화면. 로그인이 풀려 있으면 /login, purpose가 틀리면 /settings")
	@GetMapping("/verify")
	@ResponseStatus(HttpStatus.FOUND)
	public ResponseEntity<Void> verify(
			@Parameter(description = "LINK 또는 WITHDRAW") @RequestParam(required = false) String purpose,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			HttpServletRequest request, HttpServletResponse response) {
		return redirect(googleOAuthService.startVerify(purpose, userId, request, response));
	}

	private static ResponseEntity<Void> redirect(URI location) {
		return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
	}

}
