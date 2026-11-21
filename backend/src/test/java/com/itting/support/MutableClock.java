package com.itting.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트에서 지금 시각을 정하고 앞으로 돌릴 수 있는 시계.
 */
public class MutableClock extends Clock {

	private Instant now;

	public MutableClock(Instant now) {
		this.now = now;
	}

	public void setNow(Instant now) {
		this.now = now;
	}

	public void advance(Duration duration) {
		this.now = now.plus(duration);
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("테스트 시계는 UTC만 쓴다");
	}

}
