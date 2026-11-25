package com.itting.notification.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.itting.notification.domain.PushSubscription;
import com.itting.notification.dto.NotificationItemResponse;
import com.itting.notification.repository.PushSubscriptionRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림이 생기면({@link NotificationCreatedEvent}) 그 사람이 등록한 모든 브라우저로 푸시를 보낸다.
 *
 * <ul>
 * <li>이벤트는 알림을 만든 트랜잭션이 커밋된 뒤에 나온다({@link NotificationEventPublisher}). 요청을 기다리게 하지 않도록
 * 보내는 일은 따로 돌린다. 줄 수가 한도를 넘으면 그 푸시는 보내지 않는다(알림 목록에는 남아 있다).
 * <li>내용은 알림 목록의 한 줄({@link NotificationItemResponse})과 같은 모양의 JSON이다. 문구와 눌렀을 때 갈 주소는
 * 프론트엔드의 서비스 워커가 만든다. 새 메시지 알림도 메시지 내용은 담지 않는다.
 * <li>메시지마다 푸시를 하나씩 보낸다. 푸시를 덮어쓰는 {@code Topic}을 붙이지 않는다.
 * <li>푸시 서비스가 {@code 404}나 {@code 410}으로 답하면 없는 구독이므로 그 줄을 지운다. 그 밖의 실패는 로그만 남기고
 * 다시 보내지 않는다.
 * <li>키가 설정되지 않았으면 아무것도 하지 않는다.
 * </ul>
 *
 * <p>로그에는 구독의 id만 남긴다. 키와 푸시 주소는 남기지 않고, 예외도 종류만 남긴다(메시지에 주소가 들어 있을 수 있다).
 */
@Component
class PushNotificationListener {

	/** 푸시를 동시에 보내는 수. */
	static final int THREADS = 4;

	/** 보내길 기다리는 알림의 한도. 넘으면 그 푸시는 보내지 않는다. */
	static final int QUEUE_CAPACITY = 1_000;

	private static final Logger log = LoggerFactory.getLogger(PushNotificationListener.class);

	private final WebPushSender sender;

	private final PushSubscriptionRepository pushSubscriptionRepository;

	private final PushSubscriptionService pushSubscriptionService;

	private final NotificationQueryService notificationQueryService;

	private final JsonMapper jsonMapper;

	private final Executor executor;

	/** 직접 만든 실행기. 서버가 꺼질 때 끈다. 테스트가 넘긴 실행기는 끄지 않으므로 null이다. */
	private final ExecutorService ownedExecutor;

	@Autowired
	PushNotificationListener(WebPushSender sender, PushSubscriptionRepository pushSubscriptionRepository,
			PushSubscriptionService pushSubscriptionService, NotificationQueryService notificationQueryService,
			JsonMapper jsonMapper) {
		this(sender, pushSubscriptionRepository, pushSubscriptionService, notificationQueryService, jsonMapper,
				newExecutor(), true);
	}

	/** 보내는 일을 돌릴 실행기를 정한다. 테스트가 같은 스레드에서 돌려 기다리지 않고 확인할 때 쓴다. */
	PushNotificationListener(WebPushSender sender, PushSubscriptionRepository pushSubscriptionRepository,
			PushSubscriptionService pushSubscriptionService, NotificationQueryService notificationQueryService,
			JsonMapper jsonMapper, Executor executor) {
		this(sender, pushSubscriptionRepository, pushSubscriptionService, notificationQueryService, jsonMapper,
				executor, false);
	}

	private PushNotificationListener(WebPushSender sender, PushSubscriptionRepository pushSubscriptionRepository,
			PushSubscriptionService pushSubscriptionService, NotificationQueryService notificationQueryService,
			JsonMapper jsonMapper, Executor executor, boolean owned) {
		this.sender = sender;
		this.pushSubscriptionRepository = pushSubscriptionRepository;
		this.pushSubscriptionService = pushSubscriptionService;
		this.notificationQueryService = notificationQueryService;
		this.jsonMapper = jsonMapper;
		this.executor = executor;
		this.ownedExecutor = owned ? (ExecutorService) executor : null;
	}

	@EventListener
	void onCreated(NotificationCreatedEvent event) {
		if (!sender.isEnabled()) {
			log.debug("브라우저 푸시 키가 설정되지 않아 푸시를 보내지 않습니다.");
			return;
		}
		try {
			executor.execute(() -> deliver(event));
		}
		catch (RejectedExecutionException e) {
			log.warn("푸시를 기다리는 알림이 너무 많아 보내지 않습니다. notificationId={}", event.notificationId());
		}
	}

	/**
	 * 그 알림의 받는 사람이 등록한 모든 브라우저로 보낸다. 한 곳이 실패해도 다음 곳으로 간다.
	 */
	void deliver(NotificationCreatedEvent event) {
		try {
			List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserId(event.userId());
			if (subscriptions.isEmpty()) {
				return;
			}
			NotificationItemResponse item = notificationQueryService.findItem(event.notificationId(), event.userId())
					.orElse(null);
			if (item == null) {
				return;
			}
			byte[] payload = jsonMapper.writeValueAsBytes(item);
			for (PushSubscription subscription : subscriptions) {
				if (!sendTo(subscription, payload)) {
					return;
				}
			}
		}
		catch (RuntimeException e) {
			log.warn("푸시를 보내지 못했습니다. notificationId={}, 원인={}", event.notificationId(), e.getClass().getSimpleName());
		}
	}

	/**
	 * @return 다음 브라우저로 이어서 보낼 수 있으면 true. 스레드가 끊겼으면 false
	 */
	private boolean sendTo(PushSubscription subscription, byte[] payload) {
		Long id = subscription.getId();
		try {
			int status = sender.send(subscription, payload);
			if (status == 404 || status == 410) {
				pushSubscriptionService.remove(id);
				log.info("푸시 서비스가 없는 구독이라고 답해 등록을 지웠습니다. pushSubscriptionId={}, status={}", id, status);
			}
			else if (status < 200 || status >= 300) {
				log.warn("푸시 서비스가 푸시를 받지 않았습니다. pushSubscriptionId={}, status={}", id, status);
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
		catch (IOException | GeneralSecurityException | RuntimeException e) {
			log.warn("푸시를 보내지 못했습니다. pushSubscriptionId={}, 원인={}", id, e.getClass().getSimpleName());
		}
		return true;
	}

	@PreDestroy
	void shutdown() {
		if (ownedExecutor != null) {
			ownedExecutor.shutdown();
			try {
				ownedExecutor.awaitTermination(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static ThreadPoolExecutor newExecutor() {
		ThreadPoolExecutor pool = new ThreadPoolExecutor(THREADS, THREADS, 30, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(QUEUE_CAPACITY), new PushThreads(), new ThreadPoolExecutor.AbortPolicy());
		pool.allowCoreThreadTimeOut(true);
		return pool;
	}

	/** 푸시를 보내는 스레드. 서버가 꺼질 때 기다리지 않는다. */
	private static final class PushThreads implements ThreadFactory {

		private final AtomicInteger count = new AtomicInteger();

		@Override
		public Thread newThread(Runnable task) {
			Thread thread = new Thread(task, "web-push-" + count.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		}

	}

}
