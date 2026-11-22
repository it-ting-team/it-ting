package com.itting.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 마이그레이션 파일 이름 규칙({@code AGENTS.md}의 DB). 여러 사람이 동시에 테이블을 만들어도 번호가 겹치지 않게
 * 만든 시각(한국 시간)을 번호로 쓴다. 규칙 전에 만든 V1 ~ V3만 예외다.
 */
class MigrationNamingTests {

	/** {@code V20261123_1430__create_meetup.sql} */
	private static final Pattern NAME = Pattern.compile("V(20[0-9]{6})_([0-2][0-9][0-5][0-9])__[a-z0-9_]+[.]sql");

	private static final Set<String> BEFORE_RULE = Set.of(
			"V1__init.sql", "V2__create_campus.sql", "V3__create_user.sql");

	@Test
	void 새_마이그레이션은_만든_시각을_번호로_쓴다() throws IOException {
		List<String> names = migrationNames();

		assertThat(names).isNotEmpty();
		assertThat(names)
				.filteredOn(name -> !BEFORE_RULE.contains(name))
				.allSatisfy(name -> assertThat(name)
						.as("마이그레이션 이름은 V<yyyyMMdd>_<HHmm>__<영어 소문자 설명>.sql 이어야 합니다")
						.matches(NAME));
	}

	@Test
	void 번호가_같은_마이그레이션은_없다() throws IOException {
		List<String> versions = migrationNames().stream()
				.map(name -> name.substring(0, name.indexOf("__")))
				.toList();

		assertThat(versions).doesNotHaveDuplicates();
	}

	private static List<String> migrationNames() throws IOException {
		Resource[] resources = new PathMatchingResourcePatternResolver()
				.getResources("file:src/main/resources/db/migration/*.sql");
		return Arrays.stream(resources).map(Resource::getFilename).sorted().toList();
	}

}
