package com.itting.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.itting.generation.dto.GenerationListResponse.GenerationResponse;
import com.itting.generation.service.GenerationProperties;
import com.itting.generation.service.GenerationService;
import org.junit.jupiter.api.Test;

class GenerationServiceTests {

	@Test
	void 설정의_순서와_상관없이_큰_기수부터_한_번씩_준다() {
		GenerationService service = new GenerationService(new GenerationProperties(List.of(14, 16, 15, 16)));

		assertThat(service.getGenerations().items())
				.extracting(GenerationResponse::generation)
				.containsExactly(16, 15, 14);
	}

	@Test
	void 설정이_없으면_빈_목록을_준다() {
		GenerationService service = new GenerationService(new GenerationProperties(null));

		assertThat(service.getGenerations().items()).isEmpty();
	}

}
