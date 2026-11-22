package com.itting.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 내장 서버를 실제로 띄워 HTTP로 부를 때도 세션 쿠키가 같은 이름과 속성으로 나가는지 본다.
 * 스프링 부트는 내장 서버가 있을 때만 쿠키 설정을 따로 만들기 때문에 MockMvc 테스트만으로는 알 수 없다.
 * 서버가 다른 스레드에서 돌아 테스트 트랜잭션을 쓸 수 없으므로 넣은 줄은 끝나고 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCookieServerTests {

	@LocalServerPort
	int port;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	private Long userId;

	@AfterEach
	void cleanUp() {
		if (userId != null) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 실제_서버도_ITTING_SESSION_쿠키를_HttpOnly_Secure_SameSite_Lax_14일로_준다() throws Exception {
		userId = testUsers.createEmailUser("server@example.com", "mole1234!");

		HttpResponse<String> login = httpClient.send(HttpRequest.newBuilder(uri("/api/v1/sessions"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("""
						{ "email": "server@example.com", "password": "mole1234!" }
						"""))
				.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(login.statusCode()).isEqualTo(201);
		List<String> cookies = login.headers().allValues("Set-Cookie");
		assertThat(cookies).hasSize(1);
		assertThat(cookies.getFirst())
				.startsWith(TestUsers.SESSION_COOKIE + "=")
				.contains("Max-Age=1209600", "Path=/", "Secure", "HttpOnly", "SameSite=Lax");

		String cookie = cookies.getFirst().substring(0, cookies.getFirst().indexOf(';'));
		HttpResponse<String> me = httpClient.send(HttpRequest.newBuilder(uri("/api/v1/me"))
				.header("Cookie", cookie)
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(me.headers().allValues("Set-Cookie")).singleElement()
				.asString().startsWith(cookie + ";").contains("Max-Age=1209600");
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

}
