package com.itting.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 테스트에서 계정 · 프로필 · 정지 기록을 DB에 바로 넣는다. 계정을 만드는 API가 생기기 전까지 쓴다.
 * 캠퍼스-층은 V2 마이그레이션의 임시 값(서울 1 ~ 20층 = id 1 ~ 20)을 쓴다.
 */
@Component
public class TestUsers {

	public static final String SESSION_COOKIE = "ITTING_SESSION";

	private final JdbcTemplate jdbcTemplate;

	private final PasswordEncoder passwordEncoder;

	/**
	 * 테스트 트랜잭션 안에서는 API 호출들이 영속성 컨텍스트를 함께 쓴다. SQL로 바꾼 값이 보이도록 바꾼 뒤 비운다.
	 */
	@PersistenceContext
	private EntityManager entityManager;

	public TestUsers(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
		this.jdbcTemplate = jdbcTemplate;
		this.passwordEncoder = passwordEncoder;
	}

	/** 이메일로 가입하고 온보딩을 마치지 않은 계정. 프로필은 15기, 서울 2층. */
	public long createEmailUser(String email, String password) {
		long userId = insertUser(email, passwordEncoder.encode(password), null, "USER", "ACTIVE");
		insertProfile(userId, 15, seoulFloorId(2));
		return userId;
	}

	/** Google로만 가입한 계정(비밀번호 없음). 로그인 이메일 칸도 비어 있다. */
	public long createGoogleOnlyUser(String googleEmail) {
		long userId = insertUser(null, null, googleEmail, "USER", "ACTIVE");
		insertProfile(userId, 16, seoulFloorId(1));
		return userId;
	}

	public long createAdmin() {
		return insertUser(null, null, null, "ADMIN", "ACTIVE");
	}

	public void markOnboarded(long userId) {
		jdbcTemplate.update("UPDATE `user` SET onboarded_at = UTC_TIMESTAMP(6) WHERE id = ?", userId);
		entityManager.clear();
	}

	public void setMattermostId(long userId, String mattermostId) {
		jdbcTemplate.update("UPDATE `user` SET mattermost_id = ? WHERE id = ?", mattermostId, userId);
		entityManager.clear();
	}

	/** 탈퇴 처리처럼 상태를 바꾸고 로그인 값을 비운다. */
	public void withdraw(long userId) {
		jdbcTemplate.update("""
				UPDATE `user` SET status = 'WITHDRAWN', email = NULL, password_hash = NULL, mattermost_id = NULL
				WHERE id = ?
				""", userId);
		entityManager.clear();
	}

	public void suspend(long userId, long adminId, String reason, Instant startsAt, Instant endsAt, Instant liftedAt) {
		jdbcTemplate.update("""
				INSERT INTO user_suspension (user_id, reason, starts_at, ends_at, imposed_by, lifted_at, lifted_by,
				                             created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", userId, reason, utc(startsAt), utc(endsAt), adminId, liftedAt == null ? null : utc(liftedAt),
				liftedAt == null ? null : adminId);
	}

	/** JDBC 드라이버가 JVM 시간대로 바꾸지 않게 UTC의 날짜 · 시각 그대로 넘긴다. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	public long seoulFloorId(int floor) {
		return jdbcTemplate.queryForObject("""
				SELECT f.id FROM campus_floor f JOIN campus c ON c.id = f.campus_id
				WHERE c.name = '서울' AND f.floor = ?
				""", Long.class, floor);
	}

	/** 응답이 준 세션 쿠키 가운데 마지막 것. 같은 이름이 여러 번 오면 브라우저는 마지막 것을 남긴다. */
	public static String lastSessionCookieHeader(MockHttpServletResponse response) {
		List<String> headers = response.getHeaders("Set-Cookie").stream()
				.filter(header -> header.startsWith(SESSION_COOKIE + "="))
				.toList();
		return headers.isEmpty() ? null : headers.getLast();
	}

	/** 응답이 준 세션 쿠키를 다음 요청에 실을 쿠키로 바꾼다. */
	public static Cookie sessionCookie(MockHttpServletResponse response) {
		String header = lastSessionCookieHeader(response);
		if (header == null) {
			throw new AssertionError("응답에 세션 쿠키가 없습니다.");
		}
		String value = header.substring(SESSION_COOKIE.length() + 1, header.indexOf(';'));
		return new Cookie(SESSION_COOKIE, value);
	}

	private long insertUser(String email, String passwordHash, String googleEmail, String role, String status) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO `user` (email, password_hash, google_sub, google_email, mattermost_id,
					                    ssafy_verified_at, terms_agreed_at, role, status, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
					""", new String[] { "id" });
			statement.setString(1, email);
			statement.setString(2, passwordHash);
			statement.setString(3, googleEmail == null ? null : "google-" + googleEmail);
			statement.setString(4, googleEmail);
			statement.setString(5, "mm-" + System.nanoTime());
			statement.setString(6, role);
			statement.setString(7, status);
			return statement;
		}, keyHolder);
		return keyHolder.getKey().longValue();
	}

	private void insertProfile(long userId, int generation, long campusFloorId) {
		jdbcTemplate.update("""
				INSERT INTO user_profile (user_id, generation, campus_floor_id, created_at, updated_at)
				VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", userId, generation, campusFloorId);
	}

}
