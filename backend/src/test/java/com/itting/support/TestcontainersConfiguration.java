package com.itting.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 MySQL과 Redis를 Docker 컨테이너로 띄운다. 로컬 compose.yaml과 같은 이미지와 설정을 쓴다.
 * DB나 세션을 쓰는 테스트는 {@code @Import(TestcontainersConfiguration.class)}를 붙인다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"))
				.withCommand(
						"--character-set-server=utf8mb4",
						"--collation-server=utf8mb4_0900_ai_ci",
						"--default-time-zone=+00:00");
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);
	}

}
