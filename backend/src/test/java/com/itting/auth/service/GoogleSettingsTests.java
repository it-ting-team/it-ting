package com.itting.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Google 설정({@code itting.google.*})을 공통 설정 파일에서 읽는지. 이름에 하이픈이 있어 환경 변수가 자동으로 이어지지 않으므로
 * {@code application.yml}이 {@code ITTING_GOOGLE_*}를 직접 잇는다. 스프링 컨텍스트를 띄우지 않고 설정 파일만 본다.
 * 값은 실제 비밀값이 아니다.
 */
class GoogleSettingsTests {

	@Test
	void 공통_설정이_모든_프로필에서_환경_변수_ITTING_GOOGLE_을_읽는다() throws Exception {
		GoogleProperties properties = bind(Map.of(
				"ITTING_GOOGLE_CLIENT_ID", "client-id-value",
				"ITTING_GOOGLE_CLIENT_SECRET", "client-secret-value", // gitleaks:allow (테스트용 가짜 값)
				"ITTING_GOOGLE_REDIRECT_URI", "https://api.example.com/api/v1/oauth/google/callback",
				"ITTING_GOOGLE_AUTHORIZATION_URI", "http://localhost:9000/auth",
				"ITTING_GOOGLE_TOKEN_URI", "http://localhost:9000/token",
				"ITTING_GOOGLE_USERINFO_URI", "http://localhost:9000/userinfo"));

		assertThat(properties).isEqualTo(new GoogleProperties("client-id-value", "client-secret-value",
				"https://api.example.com/api/v1/oauth/google/callback", "http://localhost:9000/auth",
				"http://localhost:9000/token", "http://localhost:9000/userinfo"));
	}

	@Test
	void 환경_변수가_없으면_클라이언트_값은_비어_있고_주소_셋은_Google의_주소다() throws Exception {
		GoogleProperties properties = bind(Map.of());

		assertThat(properties.clientId()).isEmpty();
		assertThat(properties.clientSecret()).isEmpty();
		assertThat(properties.redirectUri()).isEmpty();
		assertThat(properties.authorizationUri()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
		assertThat(properties.tokenUri()).isEqualTo("https://oauth2.googleapis.com/token");
		assertThat(properties.userinfoUri()).isEqualTo("https://openidconnect.googleapis.com/v1/userinfo");
	}

	@Test
	void 값이_null이거나_공백이면_빈_값과_Google의_주소로_채운다() {
		GoogleProperties properties = new GoogleProperties(null, "  ", null, " ", null, "");

		assertThat(properties.clientId()).isEmpty();
		assertThat(properties.clientSecret()).isEmpty();
		assertThat(properties.redirectUri()).isEmpty();
		assertThat(properties.authorizationUri()).isEqualTo(GoogleProperties.DEFAULT_AUTHORIZATION_URI);
		assertThat(properties.tokenUri()).isEqualTo(GoogleProperties.DEFAULT_TOKEN_URI);
		assertThat(properties.userinfoUri()).isEqualTo(GoogleProperties.DEFAULT_USERINFO_URI);
	}

	private static GoogleProperties bind(Map<String, Object> environment) throws Exception {
		MutablePropertySources sources = new MutablePropertySources();
		sources.addFirst(new MapPropertySource("env", environment));
		for (PropertySource<?> source : new YamlPropertySourceLoader().load("application",
				new ClassPathResource("application.yml"))) {
			sources.addLast(source);
		}
		Binder binder = new Binder(ConfigurationPropertySources.from(sources),
				new PropertySourcesPlaceholdersResolver(sources));
		return binder.bind("itting.google", GoogleProperties.class).get();
	}

}
