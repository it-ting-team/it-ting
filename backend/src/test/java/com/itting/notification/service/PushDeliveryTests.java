package com.itting.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.itting.notification.domain.NotificationType;
import com.itting.notification.domain.PushSubscription;
import com.itting.notification.repository.PushSubscriptionRepository;
import com.itting.notification.service.WebPushTestSupport.Browser;
import com.itting.support.CommunitySteps;
import com.itting.support.FakePushServer;
import com.itting.support.FakePushServer.Received;
import com.itting.support.NotificationSteps;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림이 생기면 그 사람의 모든 브라우저로 푸시가 가는지. 브라우저의 푸시 서비스는 {@link FakePushServer}(localhost)로 흉내 내고,
 * 받은 본문은 브라우저의 키로 풀어 내용을 본다. 알림은 커밋해서 만들어야 이벤트가 나가므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은 끝에
 * 지운다.
 *
 * <p>푸시 키는 실행할 때마다 새로 만든 테스트 전용 키다(저장소에 키를 두지 않는다). 서버가 만든 푸시 보내는 쪽 대신 이 키를 쓰는 보내는 쪽을
 * 등록해, 스프링이 이벤트를 받아 따로 돌려 보내는 길을 그대로 쓴다. 푸시 주소는 서버 안쪽({@code localhost}, {@code http})이라
 * {@code allowLocalEndpoints}를 켠 보내는 쪽을 쓴다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PushDeliveryTests {

	private static final KeyPair VAPID_KEYS = WebPushKeys.generate();

	private static final String VAPID_PUBLIC_KEY = WebPushKeys.base64Url(WebPushKeys.encode((ECPublicKey) VAPID_KEYS.getPublic()));

	private static final String VAPID_PRIVATE_KEY = WebPushKeys.base64Url(WebPushKeys.encode((ECPrivateKey) VAPID_KEYS.getPrivate()));

	private static final String SUBJECT = "mailto:team@example.com";

	private static final Pattern AUTHORIZATION = Pattern.compile("vapid t=([A-Za-z0-9_-]+)[.]([A-Za-z0-9_-]+)[.]([A-Za-z0-9_-]+), k=([A-Za-z0-9_-]+)");

	private static final Set<String> ITEM_FIELDS = Set.of("id", "type", "category", "actor", "target", "isRead", "createdAt");

	@TestConfiguration(proxyBeanMethods = false)
	static class TestPushSenderConfiguration {

		@Bean
		@Primary
		WebPushSender testWebPushSender(JsonMapper jsonMapper, Clock clock) {
			return new WebPushSender(new PushProperties(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, SUBJECT, true), jsonMapper,
					clock);
		}

	}

	@Autowired
	ApplicationContext applicationContext;

	@Autowired
	NotificationService notificationService;

	@Autowired
	NotificationQueryService notificationQueryService;

	@Autowired
	PushSubscriptionRepository pushSubscriptionRepository;

	@Autowired
	PushSubscriptionService pushSubscriptionService;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps communitySteps;

	@Autowired
	NotificationSteps notificationSteps;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	Clock clock;

	FakePushServer server;

	long receiver;

	long actor;

	long stranger;

	@BeforeEach
	void setUp() {
		server = new FakePushServer();
		receiver = communitySteps.createMember("push-receiver@example.com", "받는사람");
		actor = communitySteps.createMember("push-actor@example.com", "새벽산책");
		stranger = communitySteps.createMember("push-stranger@example.com", "지나가는이");
	}

	@AfterEach
	void cleanUp() {
		server.stop();
		List<Long> users = List.of(receiver, actor, stranger);
		for (long userId : users) {
			jdbcTemplate.update("DELETE FROM push_subscription WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM notification WHERE user_id = ? OR actor_id = ?", userId, userId);
		}
		for (long userId : users) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	// 보내기

	@Test
	void 알림이_생기면_그_사람의_모든_브라우저로_푸시가_가고_다른_사람의_브라우저로는_가지_않는다() throws Exception {
		Browser first = Browser.create();
		Browser second = Browser.create();
		Browser strangers = Browser.create();
		subscribe(receiver, "/push/first", first);
		subscribe(receiver, "/push/second", second);
		subscribe(stranger, "/push/stranger", strangers);

		commit(() -> notificationService.notify(receiver, NotificationType.POST_COMMENTED, actor, 77L));

		long notificationId = notificationIdOf(receiver);
		awaitRequests(2);
		assertThat(server.receivedBy("/push/first")).hasSize(1);
		assertThat(server.receivedBy("/push/second")).hasSize(1);
		assertThat(server.receivedBy("/push/stranger")).as("다른 사람의 브라우저").isEmpty();
		for (Map.Entry<String, Browser> entry : Map.of("/push/first", first, "/push/second", second).entrySet()) {
			Received received = server.receivedBy(entry.getKey()).getFirst();
			assertPushHeaders(received);
			JsonNode item = jsonMapper.readTree(entry.getValue().decrypt(received.body()));
			assertThat(fieldNames(item)).isEqualTo(ITEM_FIELDS);
			assertThat(item.get("id").asLong()).isEqualTo(notificationId);
			assertThat(item.get("type").asString()).isEqualTo("POST_COMMENTED");
			assertThat(item.get("category").asString()).isEqualTo("COMMUNITY");
			assertThat(item.get("actor").get("id").asLong()).isEqualTo(actor);
			assertThat(item.get("actor").get("nickname").asString()).isEqualTo("새벽산책");
			assertThat(item.get("actor").get("isWithdrawn").asBoolean()).isFalse();
			assertThat(item.get("target").get("type").asString()).isEqualTo("POST");
			assertThat(item.get("target").get("id").asLong()).isEqualTo(77L);
			assertThat(item.get("isRead").asBoolean()).isFalse();
			assertThat(item.get("createdAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
		}
		// 두 브라우저는 각자의 키로 암호화되어, 다른 브라우저의 키로는 풀리지 않는다
		assertThatThrownBy(() -> second.decrypt(server.receivedBy("/push/first").getFirst().body()))
				.isInstanceOf(java.security.GeneralSecurityException.class);
	}

	@Test
	void 일으킨_사람이_없는_알림은_actor가_null이고_갈_곳의_id가_없는_알림도_보낸다() throws Exception {
		Browser browser = Browser.create();
		subscribe(receiver, "/push/a", browser);

		commit(() -> notificationService.notify(receiver, NotificationType.POST_HIDDEN, null, 5L));
		commit(() -> notificationService.notify(receiver, NotificationType.GUESTBOOK_ENTRY_CREATED, actor, null));

		awaitRequests(2);
		List<JsonNode> items = server.received().stream().map(r -> decryptItem(browser, r)).toList();
		JsonNode hidden = items.stream().filter(i -> i.get("type").asString().equals("POST_HIDDEN")).findFirst().orElseThrow();
		JsonNode guestbook = items.stream().filter(i -> i.get("type").asString().equals("GUESTBOOK_ENTRY_CREATED")).findFirst().orElseThrow();
		assertThat(hidden.get("actor").isNull()).isTrue();
		assertThat(hidden.get("target").get("id").asLong()).isEqualTo(5L);
		assertThat(guestbook.get("target").get("type").asString()).isEqualTo("GUESTBOOK");
		assertThat(guestbook.get("target").get("id").isNull()).isTrue();
	}

	@Test
	void 새_메시지_알림은_메시지마다_푸시가_하나씩_가고_Topic_헤더와_메시지_내용이_없다() throws Exception {
		Browser browser = Browser.create();
		subscribe(receiver, "/push/a", browser);

		commit(() -> notificationService.notifyChatMessage(receiver, 9L, actor));
		commit(() -> notificationService.notifyChatMessage(receiver, 9L, actor));

		assertThat(countNotifications(receiver)).as("알림은 대화방마다 한 줄이다").isEqualTo(1);
		awaitRequests(2);
		long notificationId = notificationIdOf(receiver);
		assertThat(server.received().stream().map(r -> WebPushKeys.base64Url(r.body())).distinct())
				.as("푸시마다 따로 암호화한다").hasSize(2);
		for (Received received : server.received()) {
			assertPushHeaders(received);
			assertThat(received.headers()).doesNotContainKey("topic");
			JsonNode item = decryptItem(browser, received);
			assertThat(fieldNames(item)).as("메시지 내용을 담는 칸이 없다").isEqualTo(ITEM_FIELDS);
			assertThat(item.get("id").asLong()).isEqualTo(notificationId);
			assertThat(item.get("type").asString()).isEqualTo("CHAT_MESSAGE_RECEIVED");
			assertThat(item.get("category").asString()).isEqualTo("CHAT");
			assertThat(item.get("target").get("type").asString()).isEqualTo("CHAT_ROOM");
			assertThat(item.get("target").get("id").asLong()).isEqualTo(9L);
		}
	}

	@Test
	void 푸시_서비스가_없는_구독이라고_답하면_그_줄을_지우고_그_밖의_실패는_줄을_남긴다() throws Exception {
		Map<String, Long> ids = new HashMap<>();
		Map<String, Integer> statuses = Map.of("/push/ok", 201, "/push/gone", 410, "/push/missing", 404,
				"/push/error", 500, "/push/rejected", 400, "/push/throttled", 429, "/push/unauthorized", 403);
		statuses.forEach((path, status) -> {
			server.respondWith(path, status);
			ids.put(path, subscribe(receiver, path, Browser.create()));
		});

		commit(() -> notificationService.notify(receiver, NotificationType.POST_LIKED, actor, 1L));

		awaitRequests(statuses.size());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(
				() -> assertThat(remainingSubscriptionIds()).doesNotContain(ids.get("/push/gone"), ids.get("/push/missing")));
		assertThat(remainingSubscriptionIds()).containsExactlyInAnyOrder(ids.get("/push/ok"), ids.get("/push/error"),
				ids.get("/push/rejected"), ids.get("/push/throttled"), ids.get("/push/unauthorized"));
	}

	@Test
	void 한_브라우저가_실패해도_다음_브라우저로_보내고_줄은_남긴다() throws Exception {
		subscribe(receiver, FakePushServer.refusedUrl("/push/down"), Browser.create());
		jdbcTemplate.update("""
				INSERT INTO push_subscription (user_id, endpoint, p256dh, auth, created_at)
				VALUES (?, ?, 'AAAA', 'AAAA', UTC_TIMESTAMP(6))
				""", receiver, server.url("/push/broken-keys"));
		Browser healthy = Browser.create();
		subscribe(receiver, "/push/healthy", healthy);

		commit(() -> notificationService.notify(receiver, NotificationType.POST_LIKED, actor, 1L));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(server.receivedBy("/push/healthy")).hasSize(1));
		assertThat(decryptItem(healthy, server.receivedBy("/push/healthy").getFirst()).get("type").asString())
				.isEqualTo("POST_LIKED");
		assertThat(server.receivedBy("/push/broken-keys")).as("키가 틀린 구독은 보내지 않는다").isEmpty();
		assertThat(remainingSubscriptionIds()).hasSize(3);
	}

	@Test
	void 등록한_브라우저가_없는_사람의_알림은_아무것도_보내지_않는다() throws Exception {
		subscribe(stranger, "/push/stranger", Browser.create());

		commit(() -> notificationService.notify(receiver, NotificationType.POST_LIKED, actor, 1L));
		// 같은 길로 보내는 알림이 도착하는 것을 확인한 뒤에 아무것도 오지 않았음을 본다
		commit(() -> notificationService.notify(stranger, NotificationType.POST_LIKED, actor, 1L));

		awaitRequests(1);
		assertThat(server.received().getFirst().path()).isEqualTo("/push/stranger");
	}

	// 키가 없을 때

	@Test
	void 키가_비었거나_틀리면_보내지_않고_모두_갖춘_키는_보낸다() throws Exception {
		Browser browser = Browser.create();
		subscribe(receiver, "/push/a", browser);
		long notificationId = notificationSteps.insertPostCommented(receiver, actor, 1L, Instant.now(), null);
		NotificationCreatedEvent event = new NotificationCreatedEvent(notificationId, receiver);
		String otherPrivateKey = WebPushKeys.base64Url(WebPushKeys.encode((ECPrivateKey) WebPushKeys.generate().getPrivate()));

		List<PushProperties> unusable = List.of(
				new PushProperties(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, "", true),
				new PushProperties(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, null, true),
				new PushProperties(VAPID_PUBLIC_KEY, "", SUBJECT, true),
				new PushProperties(VAPID_PUBLIC_KEY, "   ", SUBJECT, true),
				new PushProperties("", VAPID_PRIVATE_KEY, SUBJECT, true),
				new PushProperties(null, null, null, true),
				new PushProperties("not a key", VAPID_PRIVATE_KEY, SUBJECT, true),
				new PushProperties(VAPID_PUBLIC_KEY, otherPrivateKey, SUBJECT, true));
		for (PushProperties properties : unusable) {
			WebPushSender sender = new WebPushSender(properties, jsonMapper, clock);

			assertThat(sender.isEnabled()).as(properties.toString()).isFalse();
			listener(sender).onCreated(event);
		}
		assertThat(server.received()).as("키가 비었거나 틀리면 아무것도 보내지 않는다").isEmpty();

		WebPushSender complete = new WebPushSender(new PushProperties(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, SUBJECT, true),
				jsonMapper, clock);
		assertThat(complete.isEnabled()).isTrue();
		listener(complete).onCreated(event);

		assertThat(server.received()).hasSize(1);
		assertThat(decryptItem(browser, server.received().getFirst()).get("id").asLong()).isEqualTo(notificationId);
	}

	@Test
	void 설정에서_읽은_보내는_쪽은_키가_없으면_꺼져_있다() {
		Map<String, WebPushSender> senders = applicationContext.getBeansOfType(WebPushSender.class);

		assertThat(senders).hasSize(2);
		WebPushSender fromProperties = senders.get("webPushSender");
		assertThat(fromProperties).isNotNull();
		assertThat(fromProperties.isEnabled()).as("테스트 설정은 푸시 키를 비워 둔다").isFalse();
		assertThat(applicationContext.getBean(WebPushSender.class).isEnabled()).as("이 테스트가 쓰는 보내는 쪽").isTrue();
	}

	@Test
	void 없는_알림이나_브라우저가_없는_사람은_오류_없이_지나간다() {
		subscribe(receiver, "/push/a", Browser.create());
		PushNotificationListener listener = listener(applicationContext.getBean(WebPushSender.class));

		listener.deliver(new NotificationCreatedEvent(999_999_999L, receiver));
		listener.deliver(new NotificationCreatedEvent(999_999_999L, stranger));
		long othersNotification = notificationSteps.insertPostCommented(stranger, actor, 1L, Instant.now(), null);
		listener.deliver(new NotificationCreatedEvent(othersNotification, receiver));

		assertThat(server.received()).as("없는 알림, 브라우저가 없는 사람, 남의 알림").isEmpty();
	}

	// 보내는 주소

	@Test
	void 서버_안쪽_주소와_https가_아닌_주소로는_보내지_않는다() {
		WebPushSender sender = new WebPushSender(new PushProperties(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, SUBJECT, false),
				jsonMapper, clock);
		Browser browser = Browser.create();

		assertThat(sender.isEnabled()).isTrue();
		for (String endpoint : List.of("http://push.example.com/x", "http://localhost:" + 1 + "/x", "https://localhost/x",
				"https://127.0.0.1/x", "https://127.1.2.3:8443/x", "https://0.0.0.0/x", "https://10.1.2.3/x",
				"https://172.16.0.9/x", "https://192.168.1.1/x", "https://169.254.169.254/latest/meta-data",
				"https://100.64.0.1/x", "https://[::1]/x", "https://[fd00::1]/x", "https://[fe80::1]/x",
				"ftp://push.example.com/x", "https:///x")) {
			PushSubscription subscription = PushSubscription.create(receiver, endpoint, browser.p256dh(), browser.auth());

			assertThatThrownBy(() -> sender.send(subscription, "{}".getBytes(StandardCharsets.UTF_8)))
					.as(endpoint)
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void 서버_안쪽_주소의_범위() throws Exception {
		for (String internal : List.of("127.0.0.1", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.0.1",
				"169.254.169.254", "100.64.0.1", "100.127.255.255", "0.0.0.0", "224.0.0.1", "::1", "fd00::1",
				"fc00::1", "fe80::1")) {
			assertThat(WebPushSender.isInternal(InetAddress.getByName(internal))).as(internal).isTrue();
		}
		for (String external : List.of("8.8.8.8", "142.250.196.110", "172.15.0.1", "172.32.0.1", "100.63.255.255",
				"100.128.0.1", "2606:4700:4700::1111", "2a00:1450:4001:81b::200e")) {
			assertThat(WebPushSender.isInternal(InetAddress.getByName(external))).as(external).isFalse();
		}
	}

	// 도우미

	private PushNotificationListener listener(WebPushSender sender) {
		return new PushNotificationListener(sender, pushSubscriptionRepository, pushSubscriptionService,
				notificationQueryService, jsonMapper, Runnable::run);
	}

	/** 그 사람의 브라우저를 등록한다. 가짜 푸시 서버의 주소(또는 넘긴 주소)를 바로 DB에 넣는다(API의 https 검사를 거치지 않는다). */
	private long subscribe(long userId, String pathOrUrl, Browser browser) {
		String endpoint = pathOrUrl.startsWith("http") ? pathOrUrl : server.url(pathOrUrl);
		jdbcTemplate.update("""
				INSERT INTO push_subscription (user_id, endpoint, p256dh, auth, created_at)
				VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6))
				""", userId, endpoint, browser.p256dh(), browser.auth());
		return jdbcTemplate.queryForObject("SELECT id FROM push_subscription WHERE endpoint = ?", Long.class, endpoint);
	}

	/** 푸시가 n개 올 때까지 기다린 뒤, 더 오지 않는지 잠깐 지켜본다. */
	private void awaitRequests(int count) {
		await().atMost(Duration.ofSeconds(10)).until(() -> server.received().size() >= count);
		await().atMost(Duration.ofSeconds(5)).during(Duration.ofMillis(400))
				.untilAsserted(() -> assertThat(server.received()).hasSize(count));
	}

	private void commit(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	private long notificationIdOf(long userId) {
		return jdbcTemplate.queryForObject("SELECT id FROM notification WHERE user_id = ?", Long.class, userId);
	}

	private int countNotifications(long userId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Integer.class,
				userId);
	}

	private List<Long> remainingSubscriptionIds() {
		return jdbcTemplate.queryForList("SELECT id FROM push_subscription WHERE user_id = ?", Long.class, receiver);
	}

	private JsonNode decryptItem(Browser browser, Received received) {
		try {
			return jsonMapper.readTree(browser.decrypt(received.body()));
		}
		catch (java.security.GeneralSecurityException e) {
			throw new AssertionError("푸시를 풀지 못했습니다", e);
		}
	}

	private static Set<String> fieldNames(JsonNode node) {
		return StreamSupport.stream(node.propertyNames().spliterator(), false).collect(Collectors.toCollection(TreeSet::new));
	}

	/** 푸시 서비스가 받는 요청의 모양(RFC 8030, 8292, 8291). 푸시를 덮어쓰는 Topic은 없다. */
	private void assertPushHeaders(Received received) throws Exception {
		assertThat(received.method()).isEqualTo("POST");
		assertThat(received.header("TTL")).isEqualTo("86400");
		assertThat(received.header("Content-Encoding")).isEqualTo("aes128gcm");
		assertThat(received.header("Content-Type")).isEqualTo("application/octet-stream");
		assertThat(received.header("Urgency")).isEqualTo("normal");
		assertThat(received.headers()).doesNotContainKey("topic");
		assertThat(received.body().length).isGreaterThan(86 + 16);

		Matcher matcher = AUTHORIZATION.matcher(received.header("Authorization"));
		assertThat(matcher.matches()).as(received.header("Authorization")).isTrue();
		assertThat(matcher.group(4)).as("k는 공개 키").isEqualTo(VAPID_PUBLIC_KEY);
		Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
		verifier.initVerify(VAPID_KEYS.getPublic());
		verifier.update((matcher.group(1) + "." + matcher.group(2)).getBytes(StandardCharsets.US_ASCII));
		assertThat(verifier.verify(WebPushKeys.decodeBase64Url(matcher.group(3)))).as("VAPID 서명").isTrue();
		JsonNode claims = jsonMapper.readTree(WebPushKeys.decodeBase64Url(matcher.group(2)));
		assertThat(claims.get("aud").asString()).isEqualTo(URI.create(server.url("/")).getScheme() + "://localhost:"
				+ URI.create(server.url("/")).getPort());
		assertThat(claims.get("sub").asString()).isEqualTo(SUBJECT);
		assertThat(claims.get("exp").asLong()).isBetween(Instant.now().plus(Duration.ofHours(11)).getEpochSecond(),
				Instant.now().plus(Duration.ofHours(13)).getEpochSecond());
	}

}
