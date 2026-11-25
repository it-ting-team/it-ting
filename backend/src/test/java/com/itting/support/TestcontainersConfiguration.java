package com.itting.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 MySQL, Redis, Mailpit을 Docker 컨테이너로 띄운다. 로컬 compose.yaml과 같은 이미지와 설정을 쓴다.
 * Mattermost 웹후크는 {@link FakeMattermost}로, Google의 토큰 · 사용자 정보 주소는 {@link FakeGoogle}로 흉내 낸다.
 * 테스트가 실제 메일이나 Mattermost, Google로 요청하는 일은 없다.
 * 푸시 키는 비워 둔다(푸시를 보내지 않는다).
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

	@Bean
	GenericContainer<?> mailpitContainer() {
		return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27")).withExposedPorts(1025, 8025);
	}

	@Bean
	Mailpit mailpit(@Qualifier("mailpitContainer") GenericContainer<?> mailpitContainer) {
		if (!mailpitContainer.isRunning()) {
			mailpitContainer.start();
		}
		return new Mailpit("http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
	}

	@Bean(destroyMethod = "stop")
	FakeMattermost fakeMattermost() {
		return new FakeMattermost();
	}

	@Bean(destroyMethod = "stop")
	FakeGoogle fakeGoogle() {
		return new FakeGoogle();
	}

	/**
	 * 메일 서버와 Mattermost 웹후크를 테스트용으로 바꾼다. 로컬 .env의 실제 웹후크가 쓰이지 않게 기수마다 덮어쓴다.
	 *
	 * <ul>
	 * <li>15: 둘 다 성공(돌아가며 쓰기)</li>
	 * <li>16: 하나</li>
	 * <li>17: 500 다음 성공(넘기기)</li>
	 * <li>18: 400 다음 성공(지워진 웹후크 넘기기)</li>
	 * <li>19: 모두 400(아이디가 틀림 → 성공으로 답함)</li>
	 * <li>20: 500과 연결 오류(보내기 실패)</li>
	 * <li>21: 웹후크 없음</li>
	 * </ul>
	 * 15 · 16 말고는 고를 수 있는 기수에 없으므로, 쓰는 테스트가 {@code itting.generations}를 늘린다.
	 */
	@Bean
	DynamicPropertyRegistrar testServiceProperties(@Qualifier("mailpitContainer") GenericContainer<?> mailpitContainer,
			FakeMattermost fakeMattermost, FakeGoogle fakeGoogle) {
		return registry -> {
			registry.add("spring.mail.host", mailpitContainer::getHost);
			registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
			registry.add("itting.mattermost.webhooks.15",
					() -> fakeMattermost.url("ok-15a") + "," + fakeMattermost.url("ok-15b"));
			registry.add("itting.mattermost.webhooks.16", () -> fakeMattermost.url("ok-16"));
			registry.add("itting.mattermost.webhooks.17",
					() -> fakeMattermost.url("down-17") + "," + fakeMattermost.url("ok-17"));
			registry.add("itting.mattermost.webhooks.18",
					() -> fakeMattermost.url("bad-18") + "," + fakeMattermost.url("ok-18"));
			registry.add("itting.mattermost.webhooks.19",
					() -> fakeMattermost.url("bad-19a") + "," + fakeMattermost.url("bad-19b"));
			registry.add("itting.mattermost.webhooks.20",
					() -> fakeMattermost.url("down-20") + "," + FakeMattermost.refusedUrl());
			registry.add("itting.mattermost.webhooks.21", () -> "");
			// 로컬 .env의 실제 Google OAuth 값이 테스트에 섞이지 않게 덮어쓰고, 토큰 · 사용자 정보 주소는 가짜 Google로 보낸다.
			registry.add("itting.google.client-id", () -> FakeGoogle.CLIENT_ID);
			registry.add("itting.google.client-secret", () -> FakeGoogle.CLIENT_SECRET);
			registry.add("itting.google.redirect-uri", () -> FakeGoogle.REDIRECT_URI);
			registry.add("itting.google.authorization-uri", fakeGoogle::authorizationUri);
			registry.add("itting.google.token-uri", fakeGoogle::tokenUri);
			registry.add("itting.google.userinfo-uri", fakeGoogle::userinfoUri);
			// 로컬 .env의 실제 푸시 키가 테스트에 섞이지 않게 비운다. 푸시를 보내는 테스트는 키를 직접 넣은 보내는 쪽을 쓴다.
			registry.add("itting.push.public-key", () -> "");
			registry.add("itting.push.private-key", () -> "");
			registry.add("itting.push.subject", () -> "");
		};
	}

}
