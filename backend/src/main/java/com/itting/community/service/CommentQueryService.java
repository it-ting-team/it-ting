package com.itting.community.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.itting.community.domain.PostStatus;
import com.itting.community.dto.CommentListResponse;
import com.itting.community.dto.CommentResponse;
import com.itting.community.dto.CommentThreadResponse;
import com.itting.community.repository.CommentQueryRepository;
import com.itting.community.repository.CommentQueryRepository.Body;
import com.itting.community.repository.CommentQueryRepository.CommentRow;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 댓글 목록을 준다({@code docs/api/08-community.md}의 {@code GET /api/v1/posts/{postId}/comments}).
 * 데이터를 바꾸지 않는다.
 *
 * <p>내가 신고했는지({@code isReportedByMe})는 신고 테이블이 생기는 08 커뮤니티 ③(KAN-53)까지 늘 false다.
 * 본문 · 좋아요 · 사용자 표시는 댓글마다가 아니라 한 페이지 전체를 한 번에 읽는다.
 */
@Service
@Transactional(readOnly = true)
public class CommentQueryService {

	private final PostAccess postAccess;

	private final CommentQueryRepository queryRepository;

	private final UserDisplayService userDisplayService;

	public CommentQueryService(PostAccess postAccess, CommentQueryRepository queryRepository,
			UserDisplayService userDisplayService) {
		this.postAccess = postAccess;
		this.queryRepository = queryRepository;
		this.userDisplayService = userDisplayService;
	}

	/**
	 * 1. 커서 → 2. 글(가려진 글은 작성자만) → 3. 댓글 {@code size + 1}개로 다음이 있는지 → 4. 답글 모두
	 * → 5. 본문 · 좋아요 · 사용자 표시를 한 번에.
	 *
	 * @param cursor 앞 응답의 {@code nextCursor}. 처음이면 null
	 */
	public CommentListResponse getComments(Long userId, Long postId, int size, String cursor) {
		CommentCursor position = cursor == null ? null : CommentCursor.decode(cursor);
		postAccess.findReadable(userId, postId);

		List<CommentRow> fetched = queryRepository.findTopLevel(postId, position == null ? null : position.createdAt(),
				position == null ? null : position.id(), size + 1);
		boolean hasNext = fetched.size() > size;
		List<CommentRow> topLevel = hasNext ? fetched.subList(0, size) : fetched;
		List<CommentRow> replies = queryRepository.findReplies(topLevel.stream().map(CommentRow::id).toList());

		List<CommentRow> all = new ArrayList<>(topLevel);
		all.addAll(replies);
		List<Long> ids = all.stream().map(CommentRow::id).toList();
		Map<Long, Body> bodies = queryRepository.findBodies(ids);
		Map<Long, Long> likes = queryRepository.countLikes(ids);
		Set<Long> liked = queryRepository.findLiked(userId, ids);
		Map<Long, UserDisplayResponse> displays = userDisplayService.getAll(userIds(all));
		Assembler assembler = new Assembler(userId, bodies, likes, liked, displays);

		Map<Long, List<CommentResponse>> repliesByParent = replies.stream()
				.collect(Collectors.groupingBy(CommentRow::parentId,
						Collectors.mapping(assembler::assemble, Collectors.toList())));
		List<CommentThreadResponse> items = topLevel.stream()
				.map(row -> CommentThreadResponse.of(assembler.assemble(row),
						repliesByParent.getOrDefault(row.id(), List.of())))
				.toList();
		String nextCursor = null;
		if (hasNext) {
			CommentRow last = topLevel.getLast();
			nextCursor = new CommentCursor(last.createdAt(), last.id()).encode();
		}
		return new CommentListResponse(items, nextCursor, hasNext);
	}

	/** 쓴 사람과 "@닉네임"으로 보여 줄 사람. */
	private static Set<Long> userIds(List<CommentRow> rows) {
		Set<Long> ids = new HashSet<>();
		for (CommentRow row : rows) {
			ids.add(row.authorId());
			if (row.mentionUserId() != null) {
				ids.add(row.mentionUserId());
			}
		}
		return ids;
	}

	/** 한 페이지의 본문 · 좋아요 · 사용자 표시로 댓글 한 줄을 응답으로 만든다. */
	private record Assembler(Long userId, Map<Long, Body> bodies, Map<Long, Long> likes, Set<Long> liked,
			Map<Long, UserDisplayResponse> displays) {

		CommentResponse assemble(CommentRow row) {
			boolean hidden = row.status() == PostStatus.HIDDEN;
			Body body = bodies.get(row.id());
			return new CommentResponse(row.id(),
					displays.getOrDefault(row.authorId(), UserDisplayResponse.withdrawn(row.authorId())),
					hidden || body == null ? null : body.content(),
					mention(row.mentionUserId()),
					row.createdAt(),
					body != null && body.version() > 1,
					likes.getOrDefault(row.id(), 0L),
					liked.contains(row.id()),
					row.authorId().equals(userId),
					hidden,
					false);
		}

		private CommentResponse.MentionUser mention(Long mentionUserId) {
			if (mentionUserId == null) {
				return null;
			}
			UserDisplayResponse display = displays.get(mentionUserId);
			return new CommentResponse.MentionUser(mentionUserId, display == null ? null : display.nickname());
		}

	}

}
