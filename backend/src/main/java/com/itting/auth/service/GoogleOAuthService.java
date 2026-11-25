package com.itting.auth.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.GoogleAccount;
import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.domain.GoogleOAuthState;
import com.itting.auth.dto.SuspensionDetailResponse;
import com.itting.auth.repository.GoogleOAuthStateRepository;
import com.itting.auth.repository.SignupRepository;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google 로그인 · 가입과 설정의 Google 확인({@code docs/api/02-auth.md}, {@code docs/api/10-settings.md}).
 * 브라우저가 Google에 다녀오는 길 전체를 맡는다. 모든 결과는 브라우저를 보낼 주소로 돌려주고, 응답 틀은 쓰지 않는다.
 *
 * <ul>
 * <li>시작: {@code state}와 PKCE를 만들어 Redis에 두고, 같은 {@code state}를 쿠키 {@code ITTING_OAUTH}로도 준다.</li>
 * <li>콜백: 쿠키와 {@code state}가 같은지, 한 번만 쓰는지 본 뒤 Google에서 계정을 받아 이유(로그인 · 연동 확인 · 탈퇴 확인)에
 * 따라 처리한다. 콜백은 DB를 바꾸지 않는다. 가입 진행 기록과 Google 확인 기록은 Redis에만 둔다.</li>
 * </ul>
 *
 * <p>실패의 원인(Google이 거절함, 통신 오류, {@code state} 문제)은 사용자에게 알리지 않고 이유별 실패 주소로만 알린다.
 * 코드, 토큰, 보안 비밀은 로그에 남기지 않는다.
 */
@Service
public class GoogleOAuthService {

	private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

	/** 시작한 뒤 Google에서 돌아올 수 있는 시간. 쿠키와 Redis의 기록이 같은 시간 동안 남는다. */
	static final Duration STATE_TTL = Duration.ofMinutes(10);

	/** {@code state}의 모양(base64url). 서버가 만든 값만 받는다. 이 모양이 아니면 Redis를 찾아보지 않는다. */
	private static final Pattern STATE_FORMAT = Pattern.compile("[A-Za-z0-9_-]{20,100}");

	private static final String SCOPE = "openid email";

	private final GoogleProperties properties;

	private final GoogleClient googleClient;

	private final GoogleOAuthStateRepository stateRepository;

	private final GoogleOAuthCookieService cookieService;

	private final GoogleVerificationService verificationService;

	private final GoogleRedirectUrls redirects;

	private final SignupRepository signupRepository;

	private final SignupCookieService signupCookieService;

	private final VerificationProperties verificationProperties;

	private final SessionService sessionService;

	private final LoginService loginService;

	private final UserRepository userRepository;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public GoogleOAuthService(GoogleProperties properties, GoogleClient googleClient,
			GoogleOAuthStateRepository stateRepository, GoogleOAuthCookieService cookieService,
			GoogleVerificationService verificationService, GoogleRedirectUrls redirects,
			SignupRepository signupRepository, SignupCookieService signupCookieService,
			VerificationProperties verificationProperties, SessionService sessionService, LoginService loginService,
			UserRepository userRepository, Clock clock) {
		this.properties = properties;
		this.googleClient = googleClient;
		this.stateRepository = stateRepository;
		this.cookieService = cookieService;
		this.verificationService = verificationService;
		this.redirects = redirects;
		this.signupRepository = signupRepository;
		this.signupCookieService = signupCookieService;
		this.verificationProperties = verificationProperties;
		this.sessionService = sessionService;
		this.loginService = loginService;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * Google 로그인을 시작한다({@code GET /api/v1/oauth/google}).
	 *
	 * @return 브라우저를 보낼 Google 로그인 화면 주소
	 */
	public URI startLogin(HttpServletResponse response) {
		return begin(GoogleOAuthPurpose.LOGIN, null, null, response);
	}

	/**
	 * 설정에서 Google 계정 확인을 시작한다({@code GET /api/v1/oauth/google/verify}).
	 *
	 * @param purposeParam {@code purpose} 쿼리. {@code LINK}나 {@code WITHDRAW}
	 * @param userId 로그인한 계정. 로그인하지 않았으면 null
	 * @return 브라우저를 보낼 주소. 로그인이 풀려 있으면 로그인 화면, {@code purpose}가 틀리면 설정의 첫 화면, 아니면 Google
	 */
	public URI startVerify(String purposeParam, Long userId, HttpServletRequest request,
			HttpServletResponse response) {
		if (userId == null) {
			return redirects.login();
		}
		Optional<GoogleOAuthPurpose> purpose = GoogleOAuthPurpose.ofVerify(purposeParam);
		if (purpose.isEmpty()) {
			return redirects.settings();
		}
		String sessionId = currentSessionId(request);
		if (sessionId == null || findActiveUser(userId, request).isEmpty()) {
			return redirects.login();
		}
		return begin(purpose.get(), userId, sessionId, response);
	}

	/**
	 * Google이 브라우저를 돌려보낸 콜백({@code GET /api/v1/oauth/google/callback}).
	 *
	 * @param code Google이 준 인가 코드
	 * @param state Google이 돌려준 {@code state}
	 * @param googleError Google이 붙인 {@code error}(사용자가 취소하면 {@code access_denied})
	 * @param stateCookie 시작할 때 준 쿠키 {@code ITTING_OAUTH}의 값
	 * @param signupToken 이 브라우저의 이전 가입 쿠키 {@code ITTING_SIGNUP}의 값
	 * @param userId 로그인한 계정. 로그인하지 않았으면 null
	 * @return 브라우저를 보낼 프론트엔드 주소
	 */
	public URI handleCallback(String code, String state, String googleError, String stateCookie, String signupToken,
			Long userId, HttpServletRequest request, HttpServletResponse response) {
		// 쿠키는 한 번 쓰면 끝이다. 어떤 결과든 지운다.
		cookieService.expire(response);

		Optional<GoogleOAuthState> attempt = consumeAttempt(state, stateCookie);
		if (attempt.isEmpty()) {
			return redirects.failure(GoogleOAuthPurpose.LOGIN);
		}
		GoogleOAuthState record = attempt.get();
		GoogleOAuthPurpose purpose = record.purpose();

		String sessionId = currentSessionId(request);
		if (purpose != GoogleOAuthPurpose.LOGIN) {
			if (userId == null) {
				return redirects.login();
			}
			// 확인은 시작한 그 로그인 세션에서만 받는다. 다른 기기나 다른 세션에서 돌아오면 기록을 만들지 않는다.
			if (!userId.equals(record.userId()) || sessionId == null || !sessionId.equals(record.sessionId())) {
				log.info("Google 확인이 시작한 세션이 아닌 곳으로 돌아왔습니다.");
				return redirects.failure(purpose);
			}
		}
		if (googleError != null || code == null || code.isBlank()) {
			log.info("Google에서 {}을(를) 마치지 못했습니다: {}", purpose, googleError == null ? "코드 없음" : "취소 또는 거절");
			return redirects.failure(purpose);
		}

		GoogleAccount account;
		try {
			account = googleClient.fetchAccount(code, record.codeVerifier());
		}
		catch (GoogleClientException e) {
			return redirects.failure(purpose);
		}

		return switch (purpose) {
			case LOGIN -> login(account, signupToken, request, response);
			case LINK -> verifyLink(account, userId, sessionId, request);
			case WITHDRAW -> verifyWithdraw(account, userId, sessionId, request);
		};
	}

	/** 쿠키와 {@code state}가 같고 서버가 기억하는 시도이며 시간이 지나지 않았을 때만 그 시도를 준다. 시도는 읽으면서 지운다. */
	private Optional<GoogleOAuthState> consumeAttempt(String state, String stateCookie) {
		if (state == null || stateCookie == null || !STATE_FORMAT.matcher(state).matches()
				|| !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
						stateCookie.getBytes(StandardCharsets.UTF_8))) {
			log.info("Google 콜백의 state가 없거나 쿠키와 다릅니다.");
			return Optional.empty();
		}
		Instant now = Instant.now(clock);
		Optional<GoogleOAuthState> record = stateRepository.consume(state).filter(found -> !found.isExpired(now));
		if (record.isEmpty()) {
			log.info("Google 콜백의 state를 이미 썼거나 시간이 지났습니다.");
		}
		return record;
	}

	/** 로그인이다. 이미 가입한 계정이면 세션을 만들고, 처음 쓰는 Google 계정이면 가입 진행 기록을 만든다. */
	private URI login(GoogleAccount account, String previousSignupToken, HttpServletRequest request,
			HttpServletResponse response) {
		Optional<User> found = userRepository.findByGoogleSub(account.sub()).filter(User::isActive);
		if (found.isPresent()) {
			User user = found.get();
			Optional<SuspensionDetailResponse> suspension = loginService.findSuspension(user.getId());
			if (suspension.isPresent()) {
				return redirects.suspended(suspension.get().reason(), suspension.get().endsAt());
			}
			sessionService.start(request, response, user.getId(), user.getRole());
			return redirects.loggedIn();
		}
		// 처음 쓰는 Google 계정. 계정은 회원가입 3을 마칠 때 만든다. 이 브라우저의 이전 가입 진행 기록은 덮어쓴다.
		signupRepository.delete(previousSignupToken);
		String token = signupRepository.createGoogleSignup(account.sub(), account.email(), Instant.now(clock),
				verificationProperties.signupTtl());
		signupCookieService.write(response, token);
		return redirects.signup();
	}

	/** 연동 확인이다. 이미 연결된 계정이거나 다른 계정이 쓰는 Google 계정이면 기록을 만들지 않는다. */
	private URI verifyLink(GoogleAccount account, Long userId, String sessionId, HttpServletRequest request) {
		Optional<User> user = findActiveUser(userId, request);
		if (user.isEmpty()) {
			return redirects.login();
		}
		if (user.get().getGoogleSub() != null) {
			return redirects.verifyError(GoogleOAuthPurpose.LINK, AuthErrorCode.GOOGLE_ALREADY_LINKED);
		}
		if (userRepository.existsByGoogleSub(account.sub())) {
			return redirects.verifyError(GoogleOAuthPurpose.LINK, AuthErrorCode.GOOGLE_ACCOUNT_DUPLICATED);
		}
		verificationService.save(sessionId, GoogleOAuthPurpose.LINK, account);
		return redirects.verified(GoogleOAuthPurpose.LINK);
	}

	/** 탈퇴 전 본인 확인이다. 내 계정에 연결된 Google 계정과 같을 때만 기록을 만든다. */
	private URI verifyWithdraw(GoogleAccount account, Long userId, String sessionId, HttpServletRequest request) {
		Optional<User> user = findActiveUser(userId, request);
		if (user.isEmpty()) {
			return redirects.login();
		}
		if (!account.sub().equals(user.get().getGoogleSub())) {
			return redirects.verifyError(GoogleOAuthPurpose.WITHDRAW, AuthErrorCode.GOOGLE_ACCOUNT_MISMATCH);
		}
		verificationService.save(sessionId, GoogleOAuthPurpose.WITHDRAW, account);
		return redirects.verified(GoogleOAuthPurpose.WITHDRAW);
	}

	/** 계정이 없거나 탈퇴했는데 세션만 남았으면 세션을 지우고 empty. */
	private Optional<User> findActiveUser(Long userId, HttpServletRequest request) {
		Optional<User> user = userRepository.findById(userId).filter(User::isActive);
		if (user.isEmpty()) {
			sessionService.end(request);
		}
		return user;
	}

	/** 시도를 만들어 기억해 두고, 같은 {@code state}를 쿠키로 주고, Google 로그인 화면 주소를 만든다. */
	private URI begin(GoogleOAuthPurpose purpose, Long userId, String sessionId, HttpServletResponse response) {
		String state = randomToken();
		String codeVerifier = randomToken();
		Instant expiresAt = Instant.now(clock).plus(STATE_TTL);
		stateRepository.save(state, new GoogleOAuthState(purpose, userId, sessionId, codeVerifier, expiresAt),
				STATE_TTL);
		cookieService.write(response, state, STATE_TTL);
		return UriComponentsBuilder.fromUriString(properties.authorizationUri())
				.queryParam("response_type", "code")
				.queryParam("client_id", "{clientId}")
				.queryParam("redirect_uri", "{redirectUri}")
				.queryParam("scope", SCOPE)
				.queryParam("state", "{state}")
				.queryParam("code_challenge", "{codeChallenge}")
				.queryParam("code_challenge_method", "S256")
				.encode()
				.buildAndExpand(Map.of(
						"clientId", properties.clientId(),
						"redirectUri", properties.redirectUri(),
						"state", state,
						"codeChallenge", codeChallenge(codeVerifier)))
				.toUri();
	}

	/** PKCE의 {@code S256}: {@code code_verifier}의 SHA-256을 base64url로 쓴다. */
	static String codeChallenge(String codeVerifier) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 추측할 수 없는 무작위 32바이트를 base64url(43자)로 쓴다. */
	private String randomToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String currentSessionId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return session == null ? null : session.getId();
	}

}
