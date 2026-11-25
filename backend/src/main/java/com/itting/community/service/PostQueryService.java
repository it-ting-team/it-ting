package com.itting.community.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.itting.community.domain.CommunityErrorCode;
import com.itting.community.domain.PostCategory;
import com.itting.community.domain.PostRules;
import com.itting.community.domain.PostSort;
import com.itting.community.domain.PostStatus;
import com.itting.community.dto.LinkableMeetupsResponse;
import com.itting.community.dto.PostCardResponse;
import com.itting.community.dto.PostDetailResponse;
import com.itting.community.dto.PostHighlightsResponse;
import com.itting.community.dto.PostInterestCategoryResponse;
import com.itting.community.dto.PostListResponse;
import com.itting.community.dto.RelatedPostsResponse;
import com.itting.community.repository.PostQueryRepository;
import com.itting.community.repository.PostQueryRepository.PostRow;
import com.itting.global.error.BusinessException;
import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;
import com.itting.meetup.service.MeetupLinkService;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 목록, 상세, 인기 글과 관심사, 다음 읽을 글, 글에 연결할 수 있는 모임을 준다({@code docs/api/08-community.md}).
 * 데이터를 바꾸지 않는다.
 * 내가 신고했는지({@code isReportedByMe})는 내 신고 줄이 있는지로 정한다. 내용은 그대로 주고, 접어 보여 주는 것은 프론트엔드가 한다.
 */
@Service
@Transactional(readOnly = true)
public class PostQueryService {

	/** 인기 점수와 많이 이야기하는 관심사가 세는 기간. 요청 시각부터 거꾸로 168시간이다. */
	static final Duration POPULAR_WINDOW = Duration.ofDays(7);

	static final int WEEKLY_TOP_SIZE = 5;

	static final int TRENDING_SIZE = 8;

	static final int RELATED_SIZE = 3;

	private final PostQueryRepository queryRepository;

	private final UserDisplayService userDisplayService;

	private final MeetupLinkService meetupLinkService;

	private final Clock clock;

	public PostQueryService(PostQueryRepository queryRepository, UserDisplayService userDisplayService,
			MeetupLinkService meetupLinkService, Clock clock) {
		this.queryRepository = queryRepository;
		this.userDisplayService = userDisplayService;
		this.meetupLinkService = meetupLinkService;
		this.clock = clock;
	}

	/**
	 * @param keyword 앞뒤 공백을 지우고 비어 있으면 검색하지 않는다
	 */
	public PostListResponse getPosts(Long userId, PostCategory category, boolean myInterestsOnly,
			Long interestCategoryId, String keyword, PostSort sort, int page, int size) {
		String trimmed = keyword == null ? null : keyword.strip();
		PostQueryRepository.Search search = new PostQueryRepository.Search(category,
				myInterestsOnly ? userId : null, interestCategoryId,
				trimmed == null || trimmed.isEmpty() ? null : trimmed);
		long offset = (long) (page - 1) * size;
		if (offset > Integer.MAX_VALUE) {
			offset = Integer.MAX_VALUE;
		}
		PostQueryRepository.Page result = queryRepository.search(search, sort, popularSince(), (int) offset, size);
		long totalPages = (result.totalItems() + size - 1) / size;
		return new PostListResponse(cards(userId, result.ids()), page, size, result.totalItems(), totalPages);
	}

	public PostHighlightsResponse getHighlights() {
		Instant since = popularSince();
		List<Long> topIds = queryRepository.findPopular(since, WEEKLY_TOP_SIZE, true, null, List.of());
		Map<Long, PostRow> rows = queryRepository.findRows(topIds);
		List<PostHighlightsResponse.WeeklyTopPost> top = new ArrayList<>();
		for (PostRow row : rows.values()) {
			top.add(new PostHighlightsResponse.WeeklyTopPost(top.size() + 1, row.id(), row.category(), row.title()));
		}
		return new PostHighlightsResponse(top,
				queryRepository.findTrendingInterestCategories(since, TRENDING_SIZE));
	}

	/** 이 카테고리의 글을 쓸 때 내가 연결할 수 있는 모임. 순서는 모임 영역의 기준을 따른다. */
	public LinkableMeetupsResponse getLinkableMeetups(Long userId, PostCategory category) {
		return new LinkableMeetupsResponse(meetupLinkService.findLinkable(userId, category.linkScope()));
	}

	public PostDetailResponse getPost(Long userId, Long postId) {
		PostRow row = findVisible(userId, postId);
		List<Long> ids = List.of(postId);
		boolean mine = row.authorId().equals(userId);
		boolean hidden = row.status() == PostStatus.HIDDEN;
		MeetupCardResponse meetup = row.meetupId() == null ? null : meetupLinkService.findCard(row.meetupId());
		return new PostDetailResponse(row.id(), row.category(), row.title(), row.content(),
				queryRepository.findImages(postId),
				userDisplayService.get(row.authorId()),
				mine ? List.of() : queryRepository.findSharedInterests(row.authorId(), userId),
				queryRepository.findInterestCategories(ids).getOrDefault(postId, List.of()),
				meetup,
				row.createdAt(),
				row.latestVersion() > 1,
				row.viewCount(),
				queryRepository.countLikes(ids).getOrDefault(postId, 0L),
				queryRepository.countComments(ids).getOrDefault(postId, 0L),
				queryRepository.findLiked(userId, ids).contains(postId),
				queryRepository.findBookmarked(userId, ids).contains(postId),
				mine,
				hidden,
				queryRepository.findReported(userId, ids).contains(postId),
				mine && !hidden);
	}

	/** 지금 글의 태그와 대분류가 하나라도 같은 공개 글 가운데 인기 점수가 높은 3개. 태그가 없는 글이면 모든 공개 글에서. */
	public RelatedPostsResponse getRelatedPosts(Long userId, Long postId) {
		findVisible(userId, postId);
		List<Long> ids = queryRepository.findPopular(popularSince(), RELATED_SIZE, false, postId,
				queryRepository.findInterestCategoryIds(postId));
		return new RelatedPostsResponse(cards(userId, ids));
	}

	/**
	 * 볼 수 있는 글. 없거나 삭제됐으면 {@code POST_NOT_FOUND}, 가려졌고 작성자가 아니면 {@code POST_HIDDEN}.
	 */
	private PostRow findVisible(Long userId, Long postId) {
		PostRow row = queryRepository.findRows(List.of(postId)).get(postId);
		if (row == null || row.status() == PostStatus.DELETED) {
			throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
		}
		if (row.status() == PostStatus.HIDDEN && !row.authorId().equals(userId)) {
			throw new BusinessException(CommunityErrorCode.POST_HIDDEN);
		}
		return row;
	}

	/**
	 * 글 카드. 받은 id의 순서를 따르고 없는 id는 뺀다. 수와 내 상태는 글마다가 아니라 목록 전체를 한 번에 센다.
	 * 내 활동({@link MyPostQueryService})도 쓴다.
	 */
	List<PostCardResponse> cards(Long userId, List<Long> postIds) {
		Map<Long, PostRow> rows = queryRepository.findRows(postIds);
		Map<Long, UserDisplayResponse> authors = userDisplayService.getAll(rows.values().stream()
				.map(PostRow::authorId)
				.collect(Collectors.toSet()));
		Map<Long, String> thumbnails = queryRepository.findThumbnailUrls(postIds);
		Map<Long, List<PostInterestCategoryResponse>> categories =
				queryRepository.findInterestCategories(postIds);
		Map<Long, Long> likes = queryRepository.countLikes(postIds);
		Map<Long, Long> comments = queryRepository.countComments(postIds);
		Set<Long> bookmarked = queryRepository.findBookmarked(userId, postIds);
		Set<Long> reported = queryRepository.findReported(userId, postIds);
		return rows.values().stream()
				.map(row -> new PostCardResponse(row.id(), row.category(), row.title(), preview(row.content()),
						thumbnails.get(row.id()),
						authors.getOrDefault(row.authorId(), UserDisplayResponse.withdrawn(row.authorId())),
						categories.getOrDefault(row.id(), List.of()),
						row.createdAt(),
						row.viewCount(),
						likes.getOrDefault(row.id(), 0L),
						comments.getOrDefault(row.id(), 0L),
						bookmarked.contains(row.id()),
						row.status() == PostStatus.HIDDEN,
						reported.contains(row.id())))
				.toList();
	}

	/** 본문의 앞 100자. 이모지도 한 자로 센다. */
	static String preview(String content) {
		int length = content.codePointCount(0, content.length());
		if (length <= PostRules.PREVIEW_LENGTH) {
			return content;
		}
		return content.substring(0, content.offsetByCodePoints(0, PostRules.PREVIEW_LENGTH));
	}

	private Instant popularSince() {
		return Instant.now(clock).minus(POPULAR_WINDOW);
	}

}
