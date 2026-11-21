package com.itting.generation.service;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 고를 수 있는 기수. 인증 코드를 보낼 Mattermost가 연결된 기수다. 기수는 DB에 테이블이 없다.
 *
 * @param generations {@code itting.generations}
 */
@ConfigurationProperties("itting")
public record GenerationProperties(List<Integer> generations) {

	public GenerationProperties {
		generations = generations == null ? List.of() : List.copyOf(generations);
	}

}
