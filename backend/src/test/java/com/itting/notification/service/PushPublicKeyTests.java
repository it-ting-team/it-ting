package com.itting.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.itting.notification.repository.NotificationSettingRepository;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.itting.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 설정이 주는 푸시 공개 키. 설정 {@code itting.push.public-key}에서 읽고, 비어 있으면 null이다.
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

	@Test
	void itting_push_public_key_설정을_읽는다() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("itting.push.public-key", KEY)));

		PushProperties properties = binder.bind("itting.push", PushProperties.class).get();

		assertThat(properties.publicKey()).isEqualTo(KEY);
	}

	@Test
	void 설정한_키를_그대로_주고_비었거나_공백이면_null을_준다() {
		long userId = testUsers.createEmailUser("push-key@example.com", "mole1234!");

		assertThat(settingsWith(KEY).getSettings(userId).pushPublicKey()).isEqualTo(KEY);
		assertThat(settingsWith("  " + KEY + "\n").getSettings(userId).pushPublicKey()).isEqualTo(KEY);
		assertThat(settingsWith("").getSettings(userId).pushPublicKey()).isNull();
		assertThat(settingsWith("   ").getSettings(userId).pushPublicKey()).isNull();
		assertThat(settingsWith(null).getSettings(userId).pushPublicKey()).isNull();
	}

	private NotificationSettingService settingsWith(String publicKey) {
		return new NotificationSettingService(notificationSettingRepository, userRepository, notificationQueryService,
				events, new PushProperties(publicKey));
	}

}
