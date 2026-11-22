package com.itting.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.SignupSteps;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 메일 서버에 닿지 못할 때 {@code POST /api/v1/email-verifications}는 {@code 503}이고 바로 다시 시도할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, EmailSendFailureTests.UnreachableMailServer.class })
class EmailSendFailureTests {

	@TestConfiguration(proxyBeanMethods = false)
	static class UnreachableMailServer {

		/** 연결을 받지 않는 포트로 보내는 메일 서버. */
		@Bean
		@Primary
		JavaMailSender unreachableMailSender() {
			JavaMailSenderImpl sender = new JavaMailSenderImpl();
			sender.setHost("127.0.0.1");
			sender.setPort(1);
			return sender;
		}

	}

	@Autowired
	MockMvc mockMvc;

	@Test
	void 메일을_보내지_못하면_503_VERIFICATION_SEND_FAILED이고_재전송_간격을_세지_않는다() throws Exception {
		String email = SignupSteps.newEmail();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/email-verifications")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{ \"email\": \"" + email + "\" }"))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.error.code").value("VERIFICATION_SEND_FAILED"));
		}
	}

}
