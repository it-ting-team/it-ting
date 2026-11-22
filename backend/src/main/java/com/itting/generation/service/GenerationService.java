package com.itting.generation.service;

import java.util.Comparator;
import java.util.List;

import com.itting.generation.dto.GenerationListResponse;
import com.itting.generation.dto.GenerationListResponse.GenerationResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(GenerationProperties.class)
public class GenerationService {

	private final GenerationProperties properties;

	public GenerationService(GenerationProperties properties) {
		this.properties = properties;
	}

	/** 서버 설정의 기수를 큰 것부터 준다. 같은 기수가 두 번 적혀 있어도 한 번만 준다. */
	public GenerationListResponse getGenerations() {
		List<GenerationResponse> items = properties.generations().stream()
				.distinct()
				.sorted(Comparator.reverseOrder())
				.map(GenerationResponse::new)
				.toList();
		return new GenerationListResponse(items);
	}

	/** 회원가입에서 고를 수 있는 기수인지. */
	public boolean isAvailable(int generation) {
		return properties.generations().contains(generation);
	}

}
