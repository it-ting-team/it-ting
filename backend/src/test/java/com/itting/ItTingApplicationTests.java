package com.itting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.itting.support.TestcontainersConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class ItTingApplicationTests {

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@Import(TestcontainersConfiguration.class)
	@ActiveProfiles("local")
	class Local {

		@Autowired
		MockMvc mockMvc;

		@Test
		void 헬스체크가_UP을_준다() throws Exception {
			mockMvc.perform(get("/actuator/health"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("UP"));
		}

		@Test
		void API_문서를_준다() throws Exception {
			mockMvc.perform(get("/v3/api-docs"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.info.title").value("it-ting API"));
		}

		@Test
		void Swagger_화면을_준다() throws Exception {
			mockMvc.perform(get("/swagger-ui/index.html"))
					.andExpect(status().isOk());
		}

	}

	@Nested
		// 운영 서버는 메일 서버 주소(SPRING_MAIL_HOST), 프론트엔드 주소(ITTING_FRONTEND_URL), 사진 주소의 앞부분
		// (ITTING_IMAGES_PUBLIC_BASE_URL)을 환경 변수로 받는다. 없으면 서버가 뜨지 않는다. 사진 폴더는 TestcontainersConfiguration이 넣는다.
		@SpringBootTest(properties = { "spring.mail.host=localhost", "itting.frontend-url=https://itting.example",
				"itting.images.public-base-url=https://itting.example" })
	@AutoConfigureMockMvc
	@Import(TestcontainersConfiguration.class)
	@ActiveProfiles("prod")
	class Prod {

		@Autowired
		MockMvc mockMvc;

		@Test
		void 헬스체크가_UP을_준다() throws Exception {
			mockMvc.perform(get("/actuator/health"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("UP"));
		}

		@Test
		void API_문서를_열지_않는다() throws Exception {
			mockMvc.perform(get("/v3/api-docs"))
					.andExpect(status().isNotFound());
		}

	}

}
