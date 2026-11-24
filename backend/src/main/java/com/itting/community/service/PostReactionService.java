package com.itting.community.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.itting.community.domain.Post;
import com.itting.community.dto.PostBookmarkResponse;
import com.itting.community.dto.PostLikeResponse;
import com.itting.community.repository.PostBookmarkRepository;
import com.itting.community.repository.PostLikeRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글에 좋아요를 누르고 취소하고, 글을 저장하고 취소한다({@code docs/api/08-community.md}).
 *
 * <p>켜는 API(PUT)는 이미 켜져 있어도 성공이고, 끄는 API(DELETE)는 이미 꺼져 있어도 성공이다. 같은 사람이 동시에 두 번 눌러도
 * 줄은 하나이고 오류가 나지 않는다. 끄는 API는 글의 상태를 보지 않는다(지워지거나 가려진 글의 좋아요 · 저장도 취소할 수 있다).
 */
@Service
@Transactional
public class PostReactionService {

	private final PostAccess postAccess;

	private final PostLikeRepository postLikeRepository;

	private final PostBookmarkRepository postBookmarkRepository;

	private final NotificationService notificationService;

	private final Clock clock;

	public PostReactionService(PostAccess postAccess, PostLikeRepository postLikeRepository,
			PostBookmarkRepository postBookmarkRepository, NotificationService notificationService, Clock clock) {
		this.postAccess = postAccess;
		this.postLikeRepository = postLikeRepository;
		this.postBookmarkRepository = postBookmarkRepository;
		this.notificationService = notificationService;
		this.clock = clock;
	}

	/**
	 * 1. 공개 글인지 → 2. 좋아요 줄 만들기(이미 있으면 그대로) → 3. 새로 만들었으면 글 작성자에게 알림.
	 * 알림은 {@code POST_LIKED:<글 id>:<누른 사람 id>} 키로 한 번만 만든다. 취소하고 다시 눌러도 늘지 않는다.
	 */
	public PostLikeResponse like(Long userId, Long postId) {
		Post post = postAccess.findPublic(postId);
		boolean created = postLikeRepository.insertIfAbsent(postId, userId, now()) == 1;
		if (created) {
			notificationService.notifyOnce(post.getAuthorId(), NotificationType.POST_LIKED, userId, postId,
					"POST_LIKED:" + postId + ":" + userId);
		}
		return new PostLikeResponse(true, postLikeRepository.countByPostId(postId));
	}

	/** 내 좋아요 줄을 지운다. 이미 만든 알림은 지우지 않는다. */
	public PostLikeResponse unlike(Long userId, Long postId) {
		postLikeRepository.deleteMine(postId, userId);
		return new PostLikeResponse(false, postLikeRepository.countByPostId(postId));
	}

	/** 공개 글을 저장한다. 내 글도 저장할 수 있고, 알림은 만들지 않는다. */
	public PostBookmarkResponse bookmark(Long userId, Long postId) {
		postAccess.findPublic(postId);
		postBookmarkRepository.insertIfAbsent(postId, userId, now());
		return new PostBookmarkResponse(true);
	}

	/** 내 저장 줄을 지운다. */
	public PostBookmarkResponse unbookmark(Long userId, Long postId) {
		postBookmarkRepository.deleteMine(postId, userId);
		return new PostBookmarkResponse(false);
	}

	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
