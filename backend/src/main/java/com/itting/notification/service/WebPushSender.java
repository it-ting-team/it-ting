package com.itting.notification.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;

import com.itting.notification.domain.PushSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 암호화한 푸시를 브라우저의 푸시 서비스(FCM 등)로 보낸다(RFC 8030). 라이브러리 없이 {@link HttpClient}로 보낸다.
 * 내용은 {@link WebPushEncryption}이 암호화하고, 보낸 곳은 {@link VapidSigner}가 서명한다.
 *
 * <p>푸시 키(공개 키, 개인 키, 연락처)가 비어 있거나 틀렸으면 {@link #isEnabled()}가 거짓이고 아무것도 보내지 않는다.
 * 서버는 키 없이도 정상으로 뜬다.
 *
 * <p>푸시 주소는 사용자가 등록한 값이라 서버가 아무 곳으로나 요청을 보내게 될 수 있다. 그래서 {@code https} 주소만, 서버 안쪽
 * 주소(내 PC, 사설망, 링크 로컬)가 아닌 곳으로만 보내고 리다이렉트는 따라가지 않는다
 * ({@link PushProperties#allowLocalEndpoints()}를 켠 테스트만 예외).
 */
@Component
@EnableConfigurationProperties(PushProperties.class)
class WebPushSender {

	/** 푸시 서비스가 받을 수 없을 때 보관해 둘 시간. 푸시는 알림 목록을 보충하는 것이라 하루 지난 것은 보내지 않는다. */
	static final Duration TTL = Duration.ofDays(1);

	private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final VapidSigner signer;

	private final boolean allowLocalEndpoints;

	private final HttpClient httpClient;

	WebPushSender(PushProperties properties, JsonMapper jsonMapper, Clock clock) {
		this.allowLocalEndpoints = properties.allowLocalEndpoints();
		this.signer = createSigner(properties, jsonMapper, clock);
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(CONNECT_TIMEOUT)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	/** 푸시를 보낼 준비가 됐는지. 키가 비어 있거나 틀렸으면 거짓이다. */
	boolean isEnabled() {
		return signer != null;
	}

	/**
	 * 푸시 하나를 보낸다. 푸시마다 새 키로 암호화하고 서명을 붙인다. 응답을 기다린다.
	 *
	 * @param payload 암호화하기 전의 내용
	 * @return 푸시 서비스가 답한 HTTP 상태 코드. 성공은 보통 201, 없는 구독은 404나 410이다
	 * @throws IllegalStateException 푸시를 보낼 준비가 되지 않았다
	 * @throws IllegalArgumentException 보낼 수 없는 주소(https가 아니거나 서버 안쪽)이거나 구독의 키가 틀렸다
	 * @throws IOException 푸시 서비스에 닿지 못했다
	 * @throws InterruptedException 기다리다 끊겼다
	 */
	int send(PushSubscription subscription, byte[] payload)
			throws IOException, InterruptedException, GeneralSecurityException {
		if (signer == null) {
			throw new IllegalStateException("브라우저 푸시 키가 설정되지 않았습니다.");
		}
		URI endpoint = URI.create(subscription.getEndpoint());
		checkDestination(endpoint);
		byte[] body = WebPushEncryption.encrypt(payload, WebPushKeys.decodeBase64Url(subscription.getP256dh()),
				WebPushKeys.decodeBase64Url(subscription.getAuth()));
		HttpRequest request = HttpRequest.newBuilder(endpoint)
				.version("http".equalsIgnoreCase(endpoint.getScheme()) ? HttpClient.Version.HTTP_1_1
						: HttpClient.Version.HTTP_2)
				.timeout(REQUEST_TIMEOUT)
				.header("Authorization", signer.authorization(endpoint))
				.header("TTL", String.valueOf(TTL.toSeconds()))
				.header("Content-Encoding", "aes128gcm")
				.header("Content-Type", "application/octet-stream")
				.header("Urgency", "normal")
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private static VapidSigner createSigner(PushProperties properties, JsonMapper jsonMapper, Clock clock) {
		if (!properties.isConfigured()) {
			return null;
		}
		try {
			return new VapidSigner(properties.publicKey().strip(), properties.privateKey().strip(),
					properties.subject().strip(), clock, jsonMapper);
		}
		catch (IllegalArgumentException e) {
			// 키는 로그에 남기지 않는다. 서명기가 만든 메시지에는 키가 없다.
			log.warn("브라우저 푸시 키가 올바르지 않아 푸시를 보내지 않습니다. {}", e.getMessage());
			return null;
		}
	}

	private void checkDestination(URI endpoint) throws IOException {
		String scheme = endpoint.getScheme();
		boolean https = "https".equalsIgnoreCase(scheme);
		if (!https && !(allowLocalEndpoints && "http".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("https 주소로만 보냅니다.");
		}
		if (endpoint.getHost() == null) {
			throw new IllegalArgumentException("주소에 호스트가 없습니다.");
		}
		if (allowLocalEndpoints) {
			return;
		}
		for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
			if (isInternal(address)) {
				throw new IllegalArgumentException("서버 안쪽 주소로는 보내지 않습니다.");
			}
		}
	}

	/** 내 PC, 모든 주소({@code 0.0.0.0}), 링크 로컬(클라우드 메타데이터 포함), 사설망, 공유 주소 대역(100.64/10), IPv6 고유 로컬. */
	static boolean isInternal(InetAddress address) {
		if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) {
			return (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 0x40;
		}
		return (bytes[0] & 0xFE) == 0xFC;
	}

}
