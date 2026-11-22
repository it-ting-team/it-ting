package com.itting.auth.service;

import java.time.Duration;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.global.error.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증 코드를 메일로 보낸다. 로컬에서는 compose의 Mailpit이 받는다(http://localhost:8025).
 */
@Service
@EnableConfigurationProperties(VerificationMailService.MailProperties.class)
public class VerificationMailService {

	private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);

	private final JavaMailSender mailSender;

	private final MailProperties properties;

	public VerificationMailService(JavaMailSender mailSender, MailProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	/**
	 * @throws BusinessException 메일 서버에 보내지 못하면 {@code VERIFICATION_SEND_FAILED}
	 */
	public void sendCode(String email, String code, Duration codeTtl) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
			helper.setFrom(properties.from());
			helper.setTo(email);
			helper.setSubject("[디굴디굴] 이메일 인증 코드");
			helper.setText("""
					디굴디굴 가입 인증 코드는 %s입니다.
					%d분 안에 입력해 주세요.

					요청하지 않았다면 이 메일을 무시해 주세요.
					""".formatted(code, codeTtl.toMinutes()));
			mailSender.send(message);
		}
		catch (MailException | MessagingException e) {
			// 받는 사람의 이메일과 예외 내용(서버 주소가 들어 있음)은 남기지 않는다.
			log.warn("인증 메일을 보내지 못했습니다: {}", e.getClass().getSimpleName());
			throw new BusinessException(AuthErrorCode.VERIFICATION_SEND_FAILED);
		}
	}

	/**
	 * @param from 보내는 사람 주소({@code itting.mail.from})
	 */
	@ConfigurationProperties("itting.mail")
	public record MailProperties(String from) {
	}

}
