package com.itting.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 모든 응답이 API 공통 규칙의 응답 틀과 에러 모양을 지키는지 검증한다.
 * 호출 대상은 테스트 전용 {@link com.itting.support.ErrorSampleController}다.
 * 로그인이 필요한 주소라 로그인한 사용자로 부른다. 로그인하지 않았을 때는 {@code SecurityConfigTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@WithMockUser
class GlobalExceptionHandlerTests {

	private static final String SAMPLES = "/api/v1/error-samples";

	@Autowired
	MockMvc mockMvc;

	@Test
	void 성공하면_success_data_error를_모두_주고_시각은_초_단위_UTC다() throws Exception {
		mockMvc.perform(get(SAMPLES))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": { "id": 1, "createdAt": "2026-11-18T08:01:05Z", "memo": null, "tags": [] },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 돌려줄_값이_없으면_data는_null이다() throws Exception {
		mockMvc.perform(post(SAMPLES).contentType(MediaType.APPLICATION_JSON).content("""
				{ "nickname": "두더지왕", "mole": { "head": "CAP" }, "type": "TMI", "answers": [] }
				"""))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 본문_검증에_실패하면_400_INVALID_INPUT과_필드_경로를_준다() throws Exception {
		mockMvc.perform(post(SAMPLES).contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "nickname": "열세글자가넘는아주긴닉네임",
				  "mole": { "head": "TOO_LONG" },
				  "type": "TMI",
				  "answers": [ { "answer": "좋아요" }, { "answer": "" } ]
				}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INVALID_INPUT",
						    "message": "입력값이 올바르지 않습니다.",
						    "fields": [
						      { "field": "nickname", "message": "크기가 0에서 12 사이여야 합니다" },
						      { "field": "mole.head", "message": "크기가 0에서 5 사이여야 합니다" },
						      { "field": "answers[1].answer", "message": "공백일 수 없습니다" }
						    ],
						    "detail": null
						  }
						}
						""", JsonCompareMode.LENIENT));
	}

	@Test
	void 목록에_없는_enum_값이면_400_INVALID_INPUT과_그_필드를_준다() throws Exception {
		mockMvc.perform(post(SAMPLES).contentType(MediaType.APPLICATION_JSON).content("""
				{ "nickname": "두더지왕", "mole": { "head": null }, "type": "UNKNOWN" }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INVALID_INPUT",
						    "message": "입력값이 올바르지 않습니다.",
						    "fields": [ { "field": "type", "message": "형식이 올바르지 않습니다." } ],
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 본문이_JSON이_아니면_400_INVALID_INPUT이고_fields는_null이다() throws Exception {
		mockMvc.perform(post(SAMPLES).contentType(MediaType.APPLICATION_JSON).content("{ nickname"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INVALID_INPUT",
						    "message": "요청 본문을 읽을 수 없습니다.",
						    "fields": null,
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 쿼리_파라미터의_타입_범위_누락은_400_INVALID_INPUT과_파라미터_이름을_준다() throws Exception {
		String typeMismatch = """
				{ "error": { "code": "INVALID_INPUT", "fields": [ { "field": "size", "message": "형식이 올바르지 않습니다." } ] } }
				""";
		String outOfRange = """
				{ "error": { "code": "INVALID_INPUT", "fields": [ { "field": "size", "message": "50 이하여야 합니다" } ] } }
				""";
		String missing = """
				{ "error": { "code": "INVALID_INPUT", "fields": [ { "field": "size", "message": "필수 값입니다." } ] } }
				""";

		mockMvc.perform(get(SAMPLES + "/search").param("size", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json(typeMismatch, JsonCompareMode.LENIENT));
		mockMvc.perform(get(SAMPLES + "/search").param("size", "51"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json(outOfRange, JsonCompareMode.LENIENT));
		mockMvc.perform(get(SAMPLES + "/search"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json(missing, JsonCompareMode.LENIENT));
	}

	@Test
	void 영역의_실패는_그_코드의_상태와_code_detail을_준다() throws Exception {
		mockMvc.perform(get(SAMPLES + "/business"))
				.andExpect(status().isForbidden())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "SAMPLE_SUSPENDED",
						    "message": "정지된 계정입니다.",
						    "fields": null,
						    "detail": { "reason": "도배" }
						  }
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 처리하지_못한_예외는_500_INTERNAL_ERROR이고_예외_내용을_내보내지_않는다() throws Exception {
		mockMvc.perform(get(SAMPLES + "/crash"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INTERNAL_ERROR",
						    "message": "서버 오류가 발생했습니다.",
						    "fields": null,
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("SELECT"))));
	}

	@Test
	void 없는_주소는_메서드와_상관없이_404_NOT_FOUND다() throws Exception {
		String notFound = """
				{
				  "success": false,
				  "data": null,
				  "error": { "code": "NOT_FOUND", "message": "없는 주소입니다.", "fields": null, "detail": null }
				}
				""";

		mockMvc.perform(get("/api/v1/nothing"))
				.andExpect(status().isNotFound())
				.andExpect(content().json(notFound, JsonCompareMode.STRICT));
		mockMvc.perform(post("/api/v1/nothing").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound())
				.andExpect(content().json(notFound, JsonCompareMode.STRICT));
	}

	@Test
	void 허용하지_않는_메서드는_405_METHOD_NOT_ALLOWED와_Allow_헤더를_준다() throws Exception {
		mockMvc.perform(post(SAMPLES + "/business"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().string("Allow", "GET"))
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "METHOD_NOT_ALLOWED",
						    "message": "이 주소에서 허용하지 않는 메서드입니다.",
						    "fields": null,
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void JSON이_아닌_Content_Type은_415_UNSUPPORTED_MEDIA_TYPE이다() throws Exception {
		mockMvc.perform(post(SAMPLES).contentType(MediaType.TEXT_PLAIN).content("nickname=두더지왕"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "UNSUPPORTED_MEDIA_TYPE",
						    "message": "지원하지 않는 Content-Type입니다.",
						    "fields": null,
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 컨트롤러_밖의_실패도_응답_틀로_답한다() throws Exception {
		mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 401))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
		mockMvc.perform(get("/error"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().json("""
						{ "success": false, "data": null, "error": { "code": "INTERNAL_ERROR" } }
						""", JsonCompareMode.LENIENT));
	}

}
