package com.itting.notification.service;

import java.time.Clock;
import java.time.Instant;

import com.itting.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 30일이 지난 알림을 지운다({@link NotificationQueryService#RETENTION}). API는 이미 30일 안의 알림만 주므로, 표에 쌓이는
 * 것만 막는다. 한 번에 오래 잠그지 않도록 {@value #BATCH_SIZE}줄씩 트랜잭션을 나눠 지운다.
 */
@Component
public class NotificationCleanupJob {

	static final int BATCH_SIZE = 1_000;

	private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

	private final NotificationRepository notificationRepository;

	private final TransactionTemplate transactionTemplate;

	private final Clock clock;

	public NotificationCleanupJob(NotificationRepository notificationRepository,
			TransactionTemplate transactionTemplate, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	/** 매일 04:00(한국 시간). */
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void run() {
		int deleted = deleteExpired();
		log.info("30일이 지난 알림 {}줄을 지웠습니다.", deleted);
	}

	/**
	 * @return 지운 줄 수
	 */
	public int deleteExpired() {
		Instant before = Instant.now(clock).minus(NotificationQueryService.RETENTION);
		int total = 0;
		int deleted;
		do {
			deleted = transactionTemplate
					.execute(status -> notificationRepository.deleteCreatedBefore(before, BATCH_SIZE));
			total += deleted;
		}
		while (deleted == BATCH_SIZE);
		return total;
	}

}
