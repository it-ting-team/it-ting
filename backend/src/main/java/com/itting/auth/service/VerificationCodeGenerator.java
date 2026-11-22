package com.itting.auth.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** 숫자 6자리 인증 코드를 만든다. 앞자리 0을 지키려고 문자열로 준다. */
@Component
public class VerificationCodeGenerator {

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		return String.format("%06d", random.nextInt(1_000_000));
	}

}
