package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.itting.support.CommunitySteps;
import com.itting.support.TestImages;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 5MB를 넘는 사진은 서블릿이 multipart를 읽다가 끊는다({@code spring.servlet.multipart.max-file-size}). MockMvc는 이 한도를
 * 거치지 않으므로 실제 서버를 띄워 HTTP로 올려 본다. 사진 올리기의 다른 경우는 {@link ImageUploadApiTests}가 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ImageUploadSizeLimitTests {

	private static final String BOUNDARY = "itting-boundary";

	@LocalServerPort
	int port;

	@Autowired
	CommunitySteps steps;

	@Autowired
	JdbcTemplate jdbcTemplate;

	final HttpClient client = HttpClient.newHttpClient();

	long userId;

	@BeforeEach
	void setUp() {
		userId = steps.createMember("size-limit@example.com", "큰사진");
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM uploaded_image WHERE uploader_id = ?", userId);
		jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
	}

	@Test
	void 사진이_5MB를_넘으면_400_INVALID_INPUT과_file_필드로_답한다() throws Exception {
		String cookie = login();
		byte[] large = Arrays.copyOf(TestImages.jpeg(), 5 * 1024 * 1024 + 512 * 1024);

		HttpResponse<String> response = upload(cookie, large);

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"")
				.contains("\"field\":\"file\"")
				.contains("5MB 이하의 사진만 올릴 수 있습니다.");
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uploaded_image WHERE uploader_id = ?",
				Integer.class, userId)).isZero();
	}

	@Test
	void 사진이_5MB_이하면_실제_서버에서도_받는다() throws Exception {
		String cookie = login();

		HttpResponse<String> response = upload(cookie, TestImages.jpeg());

		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.body()).contains("\"imageId\"");
	}

	private String login() throws Exception {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/v1/sessions"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("""
						{ "email": "size-limit@example.com", "password": "%s" }
						""".formatted(CommunitySteps.PASSWORD)))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(201);
		return response.headers().allValues("Set-Cookie").stream()
				.filter(value -> value.startsWith("ITTING_SESSION="))
				.map(value -> value.substring(0, value.indexOf(';')))
				.findFirst()
				.orElseThrow();
	}

	private HttpResponse<String> upload(String cookie, byte[] file) throws Exception {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.writeBytes(("--" + BOUNDARY + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n"
				+ "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.writeBytes(file);
		body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return client.send(HttpRequest.newBuilder(uri("/api/v1/images"))
				.header("Cookie", cookie)
				.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
				.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

}
