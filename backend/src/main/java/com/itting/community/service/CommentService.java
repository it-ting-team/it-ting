package com.itting.community.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.itting.community.domain.Comment;
import com.itting.community.domain.CommentVersion;
import com.itting.community.domain.CommunityErrorCode;
import com.itting.community.domain.Post;
import com.itting.community.domain.PostStatus;
import com.itting.community.dto.CommentCreateRequest;
import com.itting.community.dto.CommentCreateResponse;
import com.itting.community.dto.CommentDeleteResponse;
import com.itting.community.dto.CommentLikeResponse;
import com.itting.community.dto.CommentResponse;
import com.itting.community.dto.CommentUpdateRequest;
import com.itting.community.dto.CommentUpdateResponse;
import com.itting.community.repository.CommentLikeRepository;
import com.itting.community.repository.CommentRepository;
import com.itting.community.repository.CommentVersionRepository;
import com.itting.community.repository.PostRepository;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글과 답글을 쓰고 고치고 지우고, 댓글에 좋아요를 누르고 취소한다({@code docs/api/08-community.md}).
 *
 * <p>댓글과 본문 버전, 댓글과 달린 답글을 함께 바꾸는 일은 각각 한 트랜잭션이다. 알림은 같은 트랜잭션 안에서 만든다.
 * 댓글을 목록으로 읽는 일은 {@link CommentQueryService}가 한다.
 */
@Service
@Transactional
public class CommentService {

	private final PostAccess postAccess;

	private final PostRepository postRepository;

	private final CommentRepository commentRepository;

	private final CommentVersionRepository commentVersionRepository;

	private final CommentLikeRepository commentLikeRepository;

	private final UserDisplayService userDisplayService;

	private final NotificationService notificationService;

	private final Clock clock;

	public CommentService(PostAccess postAccess, PostRepository postRepository, CommentRepository commentRepository,
			CommentVersionRepository commentVersionRepository, CommentLikeRepository commentLikeRepository,
			UserDisplayService userDisplayService, NotificationService notificationService, Clock clock) {
		this.postAccess = postAccess;
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.commentVersionRepository = commentVersionRepository;
		this.commentLikeRepository = commentLikeRepository;
		this.userDisplayService = userDisplayService;
		this.notificationService = notificationService;
		this.clock = clock;
	}

	/**
	 * 1. 공개 글인지(글을 잠금) → 2. 답글이면 답할 대상 → 3. 댓글 · 버전 1을 한 번에 저장 → 4. 알림.
	 * 댓글이면 글 작성자에게 {@code POST_COMMENTED}, 답글이면 답한 대상의 작성자에게 {@code COMMENT_REPLIED}를 보낸다.
	 * 답글은 한 단계만 둔다. 답글에 다시 답해도 같은 댓글 아래에 달고, 그 답글의 작성자를 {@code mention_user_id}에 남긴다.
	 */
	public CommentCreateResponse create(Long userId, Long postId, CommentCreateRequest request) {
		Post post = postAccess.findPublicForShare(postId);
		Comment target = request.replyToCommentId() == null ? null : findReplyTarget(postId, request.replyToCommentId());

		Comment comment;
		if (target == null) {
			comment = Comment.create(postId, userId);
		}
		else if (target.isReply()) {
			comment = Comment.reply(postId, userId, target.getParentId(), target.getAuthorId());
		}
		else {
			comment = Comment.reply(postId, userId, target.getId(), null);
		}
		comment = commentRepository.save(comment);
		commentVersionRepository.save(CommentVersion.create(comment.getId(), 1, request.content()));

		if (target == null) {
			notificationService.notify(post.getAuthorId(), NotificationType.POST_COMMENTED, userId, postId);
		}
		else {
			notificationService.notify(target.getAuthorId(), NotificationType.COMMENT_REPLIED, userId, postId);
		}

		CommentResponse created = toResponse(userId, comment, request.content());
		return CommentCreateResponse.of(created, comment.getParentId(), countComments(postId));
	}

	/**
	 * 1. 댓글 찾기(잠금) → 2. 쓴 사람 → 3. 댓글이나 글이 가려졌는지 → 4. 본문이 달라졌으면 새 버전.
	 * 가려진 글에 달린 댓글도 고칠 수 없다(KAN-52). 지워진 글의 댓글은 글과 함께 지워졌으므로 없는 댓글이다.
	 */
	public CommentUpdateResponse update(Long userId, Long commentId, CommentUpdateRequest request) {
		Comment comment = commentRepository.findByIdForUpdate(commentId)
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND));
		if (!comment.isWrittenBy(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		Post post = postRepository.findById(comment.getPostId())
				.orElseThrow(() -> new IllegalStateException("글이 없는 댓글: " + commentId));
		if (post.isDeleted()) {
			throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
		}
		if (comment.isHidden() || post.isHidden()) {
			throw new BusinessException(CommunityErrorCode.COMMENT_EDIT_NOT_ALLOWED);
		}

		CommentVersion latest = commentVersionRepository.findTopByCommentIdOrderByVersionDesc(commentId)
				.orElseThrow(() -> new IllegalStateException("버전이 없는 댓글: " + commentId));
		int version = latest.getVersion();
		if (latest.differsFrom(request.content())) {
			version++;
			commentVersionRepository.save(CommentVersion.create(commentId, version, request.content()));
		}
		return new CommentUpdateResponse(request.content(), version > 1);
	}

	/**
	 * 1. 댓글 찾기(잠금) → 2. 쓴 사람 → 3. 지움 → 4. 맨 위 댓글이면 달린 답글도 지움. 3과 4는 한 번에 처리한다.
	 * 가려진 댓글과 가려진 글에 달린 내 댓글도 지울 수 있다. 글 작성자가 남의 댓글을 지울 수는 없다.
	 */
	public CommentDeleteResponse delete(Long userId, Long commentId) {
		Comment comment = commentRepository.findByIdForUpdate(commentId)
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND));
		if (!comment.isWrittenBy(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		Instant now = now();
		comment.delete(now);
		if (!comment.isReply()) {
			commentRepository.deleteRepliesOf(commentId, now);
		}
		return new CommentDeleteResponse(countComments(comment.getPostId()));
	}

	/**
	 * 1. 공개 댓글이고 공개 글의 댓글인지 → 2. 좋아요 줄 만들기(이미 있으면 그대로) → 3. 새로 만들었으면 댓글 작성자에게 알림.
	 * 알림은 {@code COMMENT_LIKED:<댓글 id>:<누른 사람 id>} 키로 한 번만 만들고, 알림이 가는 곳은 그 댓글이 달린 글이다.
	 */
	public CommentLikeResponse like(Long userId, Long commentId) {
		Comment comment = commentRepository.findById(commentId)
				.filter(Comment::isPublic)
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND));
		boolean postOpen = postRepository.findById(comment.getPostId()).filter(Post::isPublic).isPresent();
		if (!postOpen) {
			throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
		}
		boolean created = commentLikeRepository.insertIfAbsent(commentId, userId, now()) == 1;
		if (created) {
			notificationService.notifyOnce(comment.getAuthorId(), NotificationType.COMMENT_LIKED, userId,
					comment.getPostId(), "COMMENT_LIKED:" + commentId + ":" + userId);
		}
		return new CommentLikeResponse(true, commentLikeRepository.countByCommentId(commentId));
	}

	/** 내 좋아요 줄을 지운다. 댓글의 상태를 보지 않고, 이미 만든 알림은 지우지 않는다. */
	public CommentLikeResponse unlike(Long userId, Long commentId) {
		commentLikeRepository.deleteMine(commentId, userId);
		return new CommentLikeResponse(false, commentLikeRepository.countByCommentId(commentId));
	}

	/**
	 * 답할 대상. 이 글의 공개 댓글이나 답글이어야 하고, 아니면 {@code COMMENT_NOT_FOUND}다. 대상이 달린 맨 위 댓글을 잠가,
	 * 그 댓글을 지우는 트랜잭션과 순서가 정해지게 한다(지워지는 댓글에 답글이 새로 달리지 않는다).
	 */
	private Comment findReplyTarget(Long postId, Long replyToCommentId) {
		Comment target = commentRepository.findById(replyToCommentId)
				.filter(found -> found.getPostId().equals(postId) && found.isPublic())
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND));
		Long topId = target.isReply() ? target.getParentId() : target.getId();
		String topStatus = commentRepository.lockStatus(topId)
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND));
		if (PostStatus.DELETED.name().equals(topStatus)) {
			throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
		}
		return target;
	}

	/** 방금 쓴 댓글을 응답의 모양으로. 새 댓글이라 좋아요도 수정도 신고도 없다. */
	private CommentResponse toResponse(Long userId, Comment comment, String content) {
		Set<Long> userIds = new HashSet<>();
		userIds.add(userId);
		if (comment.getMentionUserId() != null) {
			userIds.add(comment.getMentionUserId());
		}
		Map<Long, UserDisplayResponse> displays = userDisplayService.getAll(userIds);
		CommentResponse.MentionUser mentionUser = null;
		if (comment.getMentionUserId() != null) {
			UserDisplayResponse mention = displays.get(comment.getMentionUserId());
			mentionUser = new CommentResponse.MentionUser(comment.getMentionUserId(),
					mention == null ? null : mention.nickname());
		}
		return new CommentResponse(comment.getId(),
				displays.getOrDefault(userId, UserDisplayResponse.withdrawn(userId)), content, mentionUser,
				comment.getCreatedAt(), false, 0, false, true, false, false);
	}

	/** 그 글의 댓글과 답글 가운데 삭제되지 않은 것의 수. 가려진 것도 센다. */
	private long countComments(Long postId) {
		return commentRepository.countByPostIdAndStatusNot(postId, PostStatus.DELETED);
	}

	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
