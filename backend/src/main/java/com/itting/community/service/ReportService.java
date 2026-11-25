package com.itting.community.service;

import com.itting.community.domain.Comment;
import com.itting.community.domain.CommentVersion;
import com.itting.community.domain.CommunityErrorCode;
import com.itting.community.domain.Post;
import com.itting.community.domain.PostVersion;
import com.itting.community.domain.Report;
import com.itting.community.domain.ReportReason;
import com.itting.community.domain.ReportRules;
import com.itting.community.domain.ReportTargetType;
import com.itting.community.dto.ReportCreateRequest;
import com.itting.community.dto.ReportCreateResponse;
import com.itting.community.repository.CommentRepository;
import com.itting.community.repository.CommentVersionRepository;
import com.itting.community.repository.PostRepository;
import com.itting.community.repository.PostVersionRepository;
import com.itting.community.repository.ReportRepository;
import com.itting.global.error.BusinessException;
import com.itting.guestbook.service.GuestbookReportService;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 · 댓글 · 사용자 · 방명록 글의 신고({@code POST /api/v1/reports}).
 *
 * <p>글 · 댓글 · 방명록 글은 대상 행을 잠근 뒤 신고를 넣고 세므로, 같은 대상의 신고는 차례로 처리된다. 마지막 복구 뒤에 접수된
 * 신고가 3건이 되는 신고에서 대상을 가리고 작성자에게 알린다. 잠근 대상이 공개일 때만 여기까지 오므로 가리기와 알림은 한 번이다.
 * 신고 · 가리기 · 알림은 한 트랜잭션이다. 사용자 신고는 쌓여도 자동으로 조치하지 않는다.
 *
 * <p>방명록 글은 방명록 영역({@link GuestbookReportService})이 읽고 가린다.
 */
@Service
public class ReportService {

	private static final String DUPLICATE_KEY = "uk_report_reporter_target";

	private final ReportRepository reportRepository;

	private final PostRepository postRepository;

	private final PostVersionRepository postVersionRepository;

	private final CommentRepository commentRepository;

	private final CommentVersionRepository commentVersionRepository;

	private final UserRepository userRepository;

	private final GuestbookReportService guestbookReportService;

	private final NotificationService notificationService;

	public ReportService(ReportRepository reportRepository, PostRepository postRepository,
			PostVersionRepository postVersionRepository, CommentRepository commentRepository,
			CommentVersionRepository commentVersionRepository, UserRepository userRepository,
			GuestbookReportService guestbookReportService, NotificationService notificationService) {
		this.reportRepository = reportRepository;
		this.postRepository = postRepository;
		this.postVersionRepository = postVersionRepository;
		this.commentRepository = commentRepository;
		this.commentVersionRepository = commentVersionRepository;
		this.userRepository = userRepository;
		this.guestbookReportService = guestbookReportService;
		this.notificationService = notificationService;
	}

	/**
	 * 신고를 받는다.
	 *
	 * @throws BusinessException 기타 사유와 {@code detail}이 맞지 않으면 {@code INVALID_INPUT}, 신고할 대상이 없으면
	 *         {@code REPORT_TARGET_NOT_FOUND}, 자기 것이면 {@code OWN_TARGET_NOT_ALLOWED}, 이미 신고했으면
	 *         {@code ALREADY_REPORTED}
	 */
	@Transactional
	public ReportCreateResponse report(Long reporterId, ReportCreateRequest request) {
		String detail = detail(request.reason(), request.detail());
		ReportTargetType targetType = request.targetType();
		Long targetId = request.targetId();
		Target target = switch (targetType) {
			case POST -> lockPost(targetId);
			case COMMENT -> lockComment(targetId);
			case MEMBER -> findMember(targetId);
			case GUESTBOOK -> lockGuestbookEntry(targetId);
		};
		if (target.ownerId().equals(reporterId)) {
			throw new BusinessException(CommunityErrorCode.OWN_TARGET_NOT_ALLOWED);
		}
		if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
			throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
		}
		Report report = save(Report.receive(reporterId, targetType, targetId, target.version(), request.reason(),
				detail));
		if (target.hide() != null
				&& reportRepository.countSinceLastRestore(targetType.name(), targetId) >= ReportRules.HIDE_THRESHOLD) {
			target.hide().run();
		}
		return new ReportCreateResponse(report.getId());
	}

	/**
	 * 잠근 대상.
	 *
	 * @param ownerId 글 · 댓글 · 방명록 글을 쓴 사람, 또는 신고받는 사용자
	 * @param version 글 · 댓글의 지금 버전 번호. 그 밖에는 null
	 * @param hide 신고가 쌓였을 때 가리고 알리는 일. 사용자면 null(자동 조치 없음)
	 */
	private record Target(Long ownerId, Integer version, Runnable hide) {
	}

	/** 공개 글. 가려지거나 삭제된 글은 신고할 수 없다. */
	private Target lockPost(Long postId) {
		Post post = postRepository.findByIdForUpdate(postId)
				.filter(Post::isPublic)
				.orElseThrow(ReportService::targetNotFound);
		int version = postVersionRepository.findTopByPostIdOrderByVersionDesc(postId)
				.map(PostVersion::getVersion)
				.orElseThrow();
		return new Target(post.getAuthorId(), version, () -> {
			post.hide();
			notificationService.notify(post.getAuthorId(), NotificationType.POST_HIDDEN, null, post.getId());
		});
	}

	/** 공개 글에 달린 공개 댓글. 가려진 글의 댓글은 다른 사람에게 보이지 않으므로 신고할 수 없다. */
	private Target lockComment(Long commentId) {
		Comment comment = commentRepository.findByIdForUpdate(commentId)
				.filter(Comment::isPublic)
				.orElseThrow(ReportService::targetNotFound);
		boolean postPublic = postRepository.findById(comment.getPostId()).map(Post::isPublic).orElse(false);
		if (!postPublic) {
			throw targetNotFound();
		}
		int version = commentVersionRepository.findTopByCommentIdOrderByVersionDesc(commentId)
				.map(CommentVersion::getVersion)
				.orElseThrow();
		return new Target(comment.getAuthorId(), version, () -> {
			comment.hide();
			notificationService.notify(comment.getAuthorId(), NotificationType.COMMENT_HIDDEN, null,
					comment.getPostId());
		});
	}

	/** 탈퇴하지 않은 사용자. 자동 조치가 없으므로 잠그지 않는다(같은 사람의 겹친 신고는 UNIQUE가 막는다). */
	private Target findMember(Long userId) {
		User user = userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(ReportService::targetNotFound);
		return new Target(user.getId(), null, null);
	}

	/** 가려지지 않은 방명록 글. */
	private Target lockGuestbookEntry(Long guestbookEntryId) {
		GuestbookReportService.ReportableEntry entry = guestbookReportService.lockReportable(guestbookEntryId)
				.orElseThrow(ReportService::targetNotFound);
		return new Target(entry.authorId(), null, () -> guestbookReportService.hideByReports(entry.id()));
	}

	/**
	 * 같은 사람이 같은 대상을 동시에 신고하면 앞의 검사를 둘 다 지나칠 수 있다. 그때는 UNIQUE에 걸린 쪽을
	 * {@code ALREADY_REPORTED}로 답한다.
	 */
	private Report save(Report report) {
		try {
			return reportRepository.saveAndFlush(report);
		}
		catch (DataIntegrityViolationException e) {
			String message = e.getMostSpecificCause().getMessage();
			if (message != null && message.contains(DUPLICATE_KEY)) {
				throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
			}
			throw e;
		}
	}

	/** 기타 사유는 공백만이 아닌 상세 내용이 꼭 있어야 하고, 그 밖의 사유는 상세 내용을 받지 않는다. */
	private static String detail(ReportReason reason, String detail) {
		if (reason == ReportReason.ETC) {
			if (detail == null || detail.isBlank()) {
				throw BusinessException.invalidInput("detail", "기타 사유를 써 주세요.");
			}
			return detail;
		}
		if (detail != null) {
			throw BusinessException.invalidInput("detail", "기타 사유일 때만 보냅니다.");
		}
		return null;
	}

	private static BusinessException targetNotFound() {
		return new BusinessException(CommunityErrorCode.REPORT_TARGET_NOT_FOUND);
	}

}
