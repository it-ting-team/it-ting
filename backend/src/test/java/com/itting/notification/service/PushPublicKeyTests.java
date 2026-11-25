package com.itting.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.itting.notification.repository.NotificationSettingRepository;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.itting.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 설정이 주는 푸시 공개 키. 설정 {@code itting.push.*}에서 읽고, 푸시를 보낼 수 없으면(세 값 가운데 하나라도
 * 비었거나 키가 틀림) null이다.
 * 설정을 바꿔 컨텍스트를 새로 띄우지 않고, 값을 읽는 쪽(바인딩)과 주는 쪽(서비스)을 따로 본다.
 * 기본 설정(빈 값)에서 API가 null을 주는지는 {@code NotificationSettingApiTests}가 본다.
 * {@link NotificationEventPublisher}가 이 패키지에서만 보여서 테스트도 이 패키지에 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PushPublicKeyTests {

	private static final String KEY = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U";

	private static final KeyPair VAPID_KEYS = WebPushKeys.generate();

	private static final String VAPID_PUBLIC_KEY = WebPushKeys.base64Url(WebPushKeys.encode((ECPublicKey) VAPID_KEYS.getPublic()));

	private static final String VAPID_PRIVATE_KEY = WebPushKeys.base64Url(WebPushKeys.encode((ECPrivateKey) VAPID_KEYS.getPrivate()));

	private static final String SUBJECT = "mailto:team@example.com";

	@Autowired
	NotificationSettingRepository notificationSettingRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	NotificationQueryService notificationQueryService;

	@Autowired
	NotificationEventPublisher events;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	Clock clock;

	@Test
	void itting_push_public_key_설정을_읽는다() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("itting.push.public-key", KEY)));

		PushProperties properties = binder.bind("itting.push", PushProperties.class).get();

		assertThat(properties.publicKey()).isEqualTo(KEY);
	}

	@Test
	void 개인_키와_연락처와_서버_안쪽_주소_허용_설정을_읽고_빠지면_비어_있고_허용하지_않는다() {
		Binder full = new Binder(new MapConfigurationPropertySource(Map.of(
				"itting.push.public-key", KEY,
				"itting.push.private-key", "private-value",
				"itting.push.subject", "mailto:team@example.com",
				"itting.push.allow-local-endpoints", "true")));
		Binder empty = new Binder(new MapConfigurationPropertySource(Map.of("itting.push.public-key", "")));

		PushProperties configured = full.bind("itting.push", PushProperties.class).get();
		PushProperties notConfigured = empty.bind("itting.push", PushProperties.class).get();

		assertThat(configured).isEqualTo(new PushProperties(KEY, "private-value", "mailto:team@example.com", true));
		assertThat(configured.isConfigured()).isTrue();
		assertThat(notConfigured.privateKey()).isNull();
		assertThat(notConfigured.subject()).isNull();
		assertThat(notConfigured.allowLocalEndpoints()).isFalse();
		assertThat(notConfigured.isConfigured()).isFalse();
		assertThat(new PushProperties(KEY, "private-value", " ", false).isConfigured()).isFalse();
	}

	@Test
	void 공통_설정이_모든_프로필에서_환경_변수_ITTING_PUSH_를_읽는다() throws Exception {
		MutablePropertySources sources = new MutablePropertySources();
		sources.addFirst(new MapPropertySource("env", Map.of(
				"ITTING_PUSH_PUBLIC_KEY", KEY,
				"ITTING_PUSH_PRIVATE_KEY", "private-value",
				"ITTING_PUSH_SUBJECT", SUBJECT)));
		for (PropertySource<?> source : new YamlPropertySourceLoader().load("application",
				new ClassPathResource("application.yml"))) {
			sources.addLast(source);
		}
		Binder binder = new Binder(ConfigurationPropertySources.from(sources),
				new PropertySourcesPlaceholdersResolver(sources));

		PushProperties properties = binder.bind("itting.push", PushProperties.class).get();

		assertThat(properties).isEqualTo(new PushProperties(KEY, "private-value", SUBJECT, false));
	}

	@Test
	void 푸시를_보낼_수_있을_때만_공개_키를_주고_아니면_null을_준다() {
		long userId = testUsers.createEmailUser("push-key@example.com", "mole1234!");

		assertThat(settingsWith(new PushProperties(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, SUBJECT, false))
			.getSettings(userId)
			.pushPublicKey()).isEqualTo(VAPID_PUBLIC_KEY);
		assertThat(settingsWith(new PushProperties("  " + VAPID_PUBLIC_KEY + "\n", VAPID_PRIVATE_KEY, SUBJECT, false))
			.getSettings(userId)
			.pushPublicKey()).isEqualTo(VAPID_PUBLIC_KEY);

		String otherPrivateKey = WebPushKeys.base64Url(WebPushKeys.encode((ECPrivateKey) WebPushKeys.generate().getPrivate()));
		List<PushProperties> unusable = List.of(
				new PushProperties(VAPID_PUBLIC_KEY, null, null, false),
				new PushProperties(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, "", false),
				new PushProperties(VAPID_PUBLIC_KEY, "", SUBJECT, false),
				new PushProperties("", VAPID_PRIVATE_KEY, SUBJECT, false),
				new PushProperties("   ", VAPID_PRIVATE_KEY, SUBJECT, false),
				new PushProperties(null, null, null, false),
				new PushProperties(VAPID_PUBLIC_KEY, otherPrivateKey, SUBJECT, false));
		for (PushProperties properties : unusable) {
			assertThat(settingsWith(properties).getSettings(userId).pushPublicKey()).as(properties.toString()).isNull();
		}
	}

	private NotificationSettingService settingsWith(PushProperties properties) {
		return new NotificationSettingService(notificationSettingRepository, userRepository, notificationQueryService,
				events, new WebPushSender(properties, jsonMapper, clock));
	}

}
