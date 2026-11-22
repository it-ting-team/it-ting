package com.itting.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 서버의 시계를 테스트가 돌릴 수 있는 {@link MutableClock}으로 바꾼다. 인증 코드 만료, 재전송 간격처럼
 * 시간이 지나야 보이는 동작을 기다리지 않고 확인한다. 테스트마다 {@code setNow(Instant.now())}로 맞춘다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock(Instant.now());
	}

}
