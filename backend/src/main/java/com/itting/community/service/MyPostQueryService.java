package com.itting.community.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.itting.community.domain.MyPostTab;
import com.itting.community.domain.PostCategory;
import com.itting.community.dto.MyPostCardResponse;
import com.itting.community.dto.MyPostListResponse;
import com.itting.community.dto.MyPostSummaryResponse;
import com.itting.community.dto.PostCardResponse;
import com.itting.community.repository.CommentQueryRepository;
import com.itting.community.repository.CommentQueryRepository.Body;
import com.itting.community.repository.MyPostQueryRepository;
import com.itting.community.repository.MyPostQueryRepository.MyComments;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 활동(내가 쓴 글, 댓글 단 글, 저장한 글)과 그 개수를 준다({@code docs/api/08-community.md}). 데이터를 바꾸지 않는다.
 * 글 카드는 글 목록과 같은 것({@link PostQueryService})을 쓴다.
 */
@Service
@Transactional(readOnly = true)
public class MyPostQueryService {

	private final MyPostQueryRepository myPostQueryRepository;

	private final CommentQueryRepository commentQueryRepository;

	private final PostQueryService postQueryService;

	public MyPostQueryService(MyPostQueryRepository myPostQueryRepository,
			CommentQueryRepository commentQueryRepository, PostQueryService postQueryService) {
		this.myPostQueryRepository = myPostQueryRepository;
		this.commentQueryRepository = commentQueryRepository;
		this.postQueryService = postQueryService;
	}

	/**
	 * @param category null이면 전체
	 * @param page 1부터 센다
	 */
	public MyPostListResponse getMyPosts(Long userId, MyPostTab tab, PostCategory category, int page, int size) {
		int offset = (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);
		MyPostQueryRepository.Page result = switch (tab) {
			case WRITTEN -> myPostQueryRepository.findWritten(userId, category, offset, size);
			case COMMENTED -> myPostQueryRepository.findCommented(userId, category, offset, size);
			case BOOKMARKED -> myPostQueryRepository.findBookmarked(userId, category, offset, size);
		};
		List<Long> ids = result.ids();
		Map<Long, Instant> bookmarkedAt = tab == MyPostTab.BOOKMARKED
				? myPostQueryRepository.findBookmarkedAt(userId, ids) : Map.of();
		Map<Long, MyComments> myComments = tab == MyPostTab.COMMENTED
				? myPostQueryRepository.findMyComments(userId, ids) : Map.of();
		Map<Long, Body> bodies = commentQueryRepository.findBodies(myComments.values().stream()
				.map(MyComments::latestCommentId)
				.toList());

		List<MyPostCardResponse> items = postQueryService.cards(userId, ids).stream()
				.map(card -> {
					MyComments mine = myComments.get(card.id());
					return MyPostCardResponse.of(card, canEdit(userId, card), bookmarkedAt.get(card.id()),
							latestComment(mine, bodies), mine == null ? 0 : mine.count());
				})
				.toList();
		long totalPages = (result.totalItems() + size - 1) / size;
		return new MyPostListResponse(items, page, size, result.totalItems(), totalPages);
	}

	public MyPostSummaryResponse getSummary(Long userId) {
		MyPostQueryRepository.Summary summary = myPostQueryRepository.summarize(userId);
		return new MyPostSummaryResponse(summary.writtenCount(), summary.commentedCount(), summary.bookmarkedCount());
	}

	/** 내 글이고 가려지지 않았으면 고칠 수 있다. */
	private static boolean canEdit(Long userId, PostCardResponse card) {
		return card.author().id().equals(userId) && !card.isHidden();
	}

	private static MyPostCardResponse.MyLatestComment latestComment(MyComments mine, Map<Long, Body> bodies) {
		if (mine == null) {
			return null;
		}
		Body body = bodies.get(mine.latestCommentId());
		return new MyPostCardResponse.MyLatestComment(mine.latestCommentId(), body == null ? null : body.content());
	}

}
