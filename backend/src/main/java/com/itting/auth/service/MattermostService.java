package com.itting.auth.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.global.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 고른 기수의 Mattermost 서버로 DM을 보낸다. 팀원 계정에서 만든 Incoming Webhook에
 * {@code channel: "@<Mattermost ID>"}를 보내면 그 사람에게 DM으로 간다.
 *
 * <ul>
 * <li>기수마다 웹후크를 여러 개 두고 돌아가며 쓴다. 한 계정에 메시지가 몰리지 않게 한다.</li>
 * <li>한 웹후크가 실패하면 같은 기수의 다음 웹후크로 다시 보낸다.</li>
 * <li>없는 아이디와 지워진 웹후크는 같은 {@code 400}으로 답해 구별할 수 없다. 모든 웹후크가 {@code 400}이면
 * 아이디가 틀린 것으로 보고 보낸 것처럼 끝낸다(명세: 아이디가 틀려도 같은 응답).</li>
 * <li>연결 오류나 {@code 5xx}가 섞여 모두 실패하면 {@code VERIFICATION_SEND_FAILED}다.</li>
 * <li>웹후크 URL과 오류 응답 본문에는 웹후크 키가 들어 있으므로 로그에 남기지 않는다. 기수와 몇 번째 웹후크인지만 남긴다.</li>
 * </ul>
 */
@Service
@EnableConfigurationProperties(MattermostService.MattermostProperties.class)
public class MattermostService {

	private static final Logger log = LoggerFactory.getLogger(MattermostService.class);

	private final MattermostProperties properties;

	private final RestClient restClient;

	/** 기수별로 다음에 쓸 웹후크의 순번. 서버를 다시 띄우면 처음부터다. */
	private final Map<Integer, AtomicInteger> turns = new ConcurrentHashMap<>();

	public MattermostService(MattermostProperties properties) {
		this.properties = properties;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/**
	 * @throws BusinessException 웹후크가 없거나, 모두 실패했는데 연결 오류 · {@code 5xx}가 섞였으면
	 *         {@code VERIFICATION_SEND_FAILED}
	 */
	public void sendDirectMessage(int generation, String mattermostId, String text) {
		List<String> webhooks = properties.webhooksOf(generation);
		if (webhooks.isEmpty()) {
			log.warn("기수 {}에 Mattermost 웹후크가 없습니다.", generation);
			throw new BusinessException(AuthErrorCode.VERIFICATION_SEND_FAILED);
		}

		int start = Math.floorMod(turns.computeIfAbsent(generation, key -> new AtomicInteger()).getAndIncrement(),
				webhooks.size());
		boolean allBadRequest = true;
		for (int i = 0; i < webhooks.size(); i++) {
			int index = (start + i) % webhooks.size();
			try {
				restClient.post()
						.uri(URI.create(webhooks.get(index)))
						.contentType(MediaType.APPLICATION_JSON)
						.body(Map.of("channel", "@" + mattermostId, "text", text))
						.retrieve()
						.toBodilessEntity();
				return;
			}
			catch (RestClientResponseException e) {
				log.warn("Mattermost 웹후크 실패: 기수 {}, {}번째, 상태 {}", generation, index + 1, e.getStatusCode().value());
				if (e.getStatusCode().value() != HttpStatus.BAD_REQUEST.value()) {
					allBadRequest = false;
				}
			}
			catch (RestClientException e) {
				// 예외 메시지에 웹후크 URL이 들어 있으므로 종류만 남긴다.
				log.warn("Mattermost 웹후크 연결 실패: 기수 {}, {}번째, {}", generation, index + 1,
						e.getClass().getSimpleName());
				allBadRequest = false;
			}
		}
		if (allBadRequest) {
			log.info("기수 {}의 Mattermost 웹후크가 모두 400으로 답했습니다. 아이디가 틀렸을 수 있습니다.", generation);
			return;
		}
		throw new BusinessException(AuthErrorCode.VERIFICATION_SEND_FAILED);
	}

	/**
	 * @param webhooks 기수: 그 기수 Mattermost 서버의 Incoming Webhook URL 목록({@code itting.mattermost.webhooks})
	 */
	@ConfigurationProperties("itting.mattermost")
	public record MattermostProperties(Map<Integer, List<String>> webhooks) {

		public MattermostProperties {
			webhooks = webhooks == null ? Map.of() : Map.copyOf(webhooks);
		}

		/** 빈 값은 뺀다(.env에 아무것도 넣지 않은 기수). */
		List<String> webhooksOf(int generation) {
			return webhooks.getOrDefault(generation, List.of()).stream()
					.map(String::strip)
					.filter(url -> !url.isEmpty())
					.toList();
		}

	}

}
