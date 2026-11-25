package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import com.itting.community.domain.ImageRules;
import com.itting.community.service.image.ImageFormat;
import com.itting.community.service.image.ImageMetadataStripper;
import com.itting.support.CommunitySteps;
import com.itting.support.TestImages;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사진 올리기: {@code POST /api/v1/images}와 올린 사진 받기({@code GET /images/**}).
 * 서블릿이 끊는 5MB 넘는 요청은 {@link ImageUploadSizeLimitTests}가 실제 서버로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ImageUploadApiTests {

	private static final String URL_PATTERN =
			"http://localhost:8080/images/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.(jpg|png|webp)";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	long me;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", "나두더지");
		session = steps.login(mockMvc, "me@example.com");
	}

	@Test
	void JPEG를_올리면_201과_id_주소를_주고_위치_정보를_뺀_파일을_주소로_받을_수_있다() throws Exception {
		String body = upload("photo.jpg", "image/jpeg", TestImages.jpegWithMetadata(6))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.imageId").isNumber())
				.andExpect(jsonPath("$.data.url").value(matchesPattern(URL_PATTERN)))
				.andExpect(jsonPath("$.error").isEmpty())
				.andReturn().getResponse().getContentAsString();
		long imageId = Long.parseLong(body.replaceAll(".*\"imageId\":(\\d+).*", "$1"));
		String url = body.replaceAll(".*\"url\":\"([^\"]+)\".*", "$1");

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM uploaded_image WHERE id = ?", imageId);
		assertThat(row.get("uploader_id")).isEqualTo(me);
		assertThat(row.get("image_url")).isEqualTo(url);
		assertThat(url).endsWith(".jpg");

		String path = url.substring("http://localhost:8080".length());
		byte[] stored = Files.readAllBytes(storedFile(path));
		assertThat(TestImages.containsSecret(stored)).isFalse();
		assertThat(stored).isEqualTo(ImageMetadataStripper.strip(TestImages.jpegWithMetadata(6), ImageFormat.JPEG));

		mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/jpeg"))
				.andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
				.andExpect(content().bytes(stored));
	}

	@Test
	void PNG와_WebP도_올릴_수_있고_형식은_파일_이름이_아니라_내용으로_본다() throws Exception {
		upload("photo.jpg", "image/jpeg", TestImages.pngWithText())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.url").value(containsString(".png")));
		upload("photo", "application/octet-stream", TestImages.webpWithMetadata())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.url").value(containsString(".webp")));
	}

	@Test
	void 올린_사진을_글에_붙일_수_있다() throws Exception {
		String body = upload("photo.png", "image/png", TestImages.png())
				.andReturn().getResponse().getContentAsString();
		long imageId = Long.parseLong(body.replaceAll(".*\"imageId\":(\\d+).*", "$1"));

		mockMvc.perform(post("/api/v1/posts").cookie(session).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "category": "FREE", "title": "사진 글", "content": "본문", "interestCategoryIds": [],
								  "imageIds": [%d], "meetupId": null }
								""".formatted(imageId)))
				.andExpect(status().isCreated());
	}

	@Test
	void 파일이_없거나_받지_않는_형식이거나_5MB를_넘으면_400이다() throws Exception {
		mockMvc.perform(multipart("/api/v1/images").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("file"))
				.andExpect(jsonPath("$.error.fields[0].message").value(ImageRules.MISSING_MESSAGE));
		upload("empty.jpg", "image/jpeg", new byte[0])
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].message").value(ImageRules.MISSING_MESSAGE));
		upload("note.jpg", "image/jpeg", "사진이 아닌 글".getBytes())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("file"))
				.andExpect(jsonPath("$.error.fields[0].message").value(ImageRules.FORMAT_MESSAGE));
		upload("moving.gif", "image/gif", TestImages.gif())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].message").value(ImageRules.FORMAT_MESSAGE));
		upload("broken.jpg", "image/jpeg", Arrays.copyOf(TestImages.jpeg(), 20))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].message").value(ImageRules.FORMAT_MESSAGE));
		upload("header-only.webp", "image/webp", TestImages.webpHeaderOnly())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].message").value(ImageRules.FORMAT_MESSAGE));
		upload("cut.jpg", "image/jpeg", Arrays.copyOf(TestImages.jpeg(), TestImages.jpeg().length - 2))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].message").value(ImageRules.FORMAT_MESSAGE));
		byte[] tooLarge = Arrays.copyOf(TestImages.jpeg(), (int) ImageRules.MAX_BYTES + 1);
		upload("large.jpg", "image/jpeg", tooLarge)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("file"))
				.andExpect(jsonPath("$.error.fields[0].message").value("5MB 이하의 사진만 올릴 수 있습니다."));

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uploaded_image WHERE uploader_id = ?",
				Integer.class, me)).isZero();
	}

	@Test
	void multipart가_아닌_본문이면_415다() throws Exception {
		mockMvc.perform(post("/api/v1/images").cookie(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(multipart("/api/v1/images")
						.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestImages.jpeg())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 없는_사진_주소는_404다() throws Exception {
		mockMvc.perform(get("/images/2026/11/00000000-0000-0000-0000-000000000000.jpg"))
				.andExpect(status().isNotFound());
	}

	private ResultActions upload(String filename, String contentType, byte[] bytes) throws Exception {
		return mockMvc.perform(multipart("/api/v1/images")
				.file(new MockMultipartFile("file", filename, contentType, bytes))
				.cookie(session));
	}

	private static Path storedFile(String path) {
		return TestcontainersConfiguration.IMAGE_DIR.resolve(path.substring("/images/".length()));
	}

}
