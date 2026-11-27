package com.itting.community.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

import com.itting.community.repository.CommunityCleanupRepository;
import com.itting.community.repository.CommunityCleanupRepository.ImageRow;
import com.itting.community.service.image.ImageStorage;
import com.itting.global.scheduling.JobLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커뮤니티의 정리 작업(KAN-66, {@code docs/api/08-community.md}의 "서버가 스스로 하는 일").
 * <ol>
 * <li>삭제한 지 {@link #DELETED_RETENTION}이 지난 글 · 댓글의 모든 버전을 지우고, 글에서 사진을 뗀다. {@code post} ·
 * {@code comment} 줄은 {@code DELETED}로 남는다.</li>
 * <li>올린 지 {@link #UNATTACHED_RETENTION}이 지났는데 글에도 채팅 메시지에도 붙지 않은 사진의 줄을 지우고, 커밋한 뒤
 * 파일을 지운다. 1에서 뗀 사진도 여기서 지워진다.</li>
 * </ol>
 * 한 번에 오래 잠그지 않도록 {@value #BATCH_SIZE}줄씩 트랜잭션을 나눈다. 서버가 여러 대면 {@link JobLock}을 잡은 서버만
 * 돈다. 겹쳐 돌아도 같은 줄을 두 번 지우려 할 뿐이라 깨지지 않는다.
 */
@Component
public class CommunityCleanupJob {

	static final int BATCH_SIZE = 1_000;

	static final Duration DELETED_RETENTION = Duration.ofDays(14);

	static final Duration UNATTACHED_RETENTION = Duration.ofHours(24);

	static final String LOCK_NAME = "community-cleanup";

	private static final Duration LOCK_TTL = Duration.ofHours(1);

	private static final Logger log = LoggerFactory.getLogger(CommunityCleanupJob.class);

	private final CommunityCleanupRepository cleanupRepository;

	private final ImageStorage imageStorage;

	private final TransactionTemplate transactionTemplate;

	private final JobLock jobLock;

	private final Clock clock;

	public CommunityCleanupJob(CommunityCleanupRepository cleanupRepository, ImageStorage imageStorage,
			TransactionTemplate transactionTemplate, JobLock jobLock, Clock clock) {
		this.cleanupRepository = cleanupRepository;
		this.imageStorage = imageStorage;
		this.transactionTemplate = transactionTemplate;
		this.jobLock = jobLock;
		this.clock = clock;
	}

	/** 지운 양. */
	public record Result(int postVersions, int commentVersions, int detachedImages, int images, int files) {
	}

	/** 매일 04:30(한국 시간). */
	@Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
	public void run() {
		if (!runOnce()) {
			log.info("다른 서버가 커뮤니티 정리 작업을 돌리고 있어 건너뜁니다.");
		}
	}

	/**
	 * 잠금을 잡고 한 번 돈다.
	 *
	 * @return 돌았으면 true. 다른 서버가 잠금을 잡고 있으면 false
	 */
	public boolean runOnce() {
		return jobLock.runExclusively(LOCK_NAME, LOCK_TTL, () -> {
			Result result = cleanUp();
			log.info("커뮤니티 정리: 글 버전 {}줄, 댓글 버전 {}줄, 글에서 뗀 사진 {}장, 사진 {}장, 파일 {}개를 지웠습니다.",
					result.postVersions(), result.commentVersions(), result.detachedImages(), result.images(),
					result.files());
		});
	}

	/** 잠금 없이 한 번 돈다. */
	Result cleanUp() {
		Instant now = Instant.now(clock);
		Instant deletedBefore = now.minus(DELETED_RETENTION);
		int postVersions = repeat(() -> cleanupRepository.deletePostVersions(deletedBefore, BATCH_SIZE));
		int commentVersions = repeat(() -> cleanupRepository.deleteCommentVersions(deletedBefore, BATCH_SIZE));
		int detachedImages = repeat(() -> cleanupRepository.deletePostImages(deletedBefore, BATCH_SIZE));

		Instant uploadedBefore = now.minus(UNATTACHED_RETENTION);
		int images = 0;
		int files = 0;
		List<ImageRow> picked;
		do {
			picked = cleanupRepository.findUnattachedImages(uploadedBefore, BATCH_SIZE);
			List<ImageRow> batch = picked;
			List<ImageRow> deleted = transactionTemplate.execute(status -> deleteRows(batch));
			images += deleted.size();
			files += deleteFiles(deleted);
		}
		while (picked.size() == BATCH_SIZE);
		return new Result(postVersions, commentVersions, detachedImages, images, files);
	}

	private int repeat(IntSupplier deleteBatch) {
		int total = 0;
		int deleted;
		do {
			deleted = transactionTemplate.execute(status -> deleteBatch.getAsInt());
			total += deleted;
		}
		while (deleted == BATCH_SIZE);
		return total;
	}

	/** @return 줄을 지운 사진. 고른 뒤 글이나 메시지에 붙은 사진은 빠진다 */
	private List<ImageRow> deleteRows(List<ImageRow> picked) {
		List<Long> ids = picked.stream().map(ImageRow::id).toList();
		if (cleanupRepository.deleteUnattachedImages(ids) == 0) {
			return List.of();
		}
		Set<Long> remaining = new HashSet<>(cleanupRepository.findExistingImageIds(ids));
		return picked.stream().filter(image -> !remaining.contains(image.id())).toList();
	}

	/** 줄을 지운 뒤에 파일을 지운다. 못 지운 파일은 남기고 넘어간다(사용자에게는 보이지 않는다). */
	private int deleteFiles(List<ImageRow> images) {
		int deleted = 0;
		for (ImageRow image : images) {
			try {
				if (imageStorage.delete(image.imageUrl())) {
					deleted++;
				}
			}
			catch (RuntimeException e) {
				log.warn("사진 파일을 지우지 못했습니다(사진 id {}): {}", image.id(), e.getMessage());
			}
		}
		return deleted;
	}

}
