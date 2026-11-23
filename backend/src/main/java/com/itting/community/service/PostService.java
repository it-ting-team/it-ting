package com.itting.community.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.itting.community.domain.CommunityErrorCode;
import com.itting.community.domain.Post;
import com.itting.community.domain.PostImage;
import com.itting.community.domain.PostInterest;
import com.itting.community.domain.PostVersion;
import com.itting.community.domain.UploadedImage;
import com.itting.community.dto.PostCreateRequest;
import com.itting.community.dto.PostCreateResponse;
import com.itting.community.dto.PostInput;
import com.itting.community.dto.PostUpdateRequest;
import com.itting.community.dto.PostViewResponse;
import com.itting.community.repository.PostImageRepository;
import com.itting.community.repository.PostInterestRepository;
import com.itting.community.repository.PostRepository;
import com.itting.community.repository.PostVersionRepository;
import com.itting.community.repository.UploadedImageRepository;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.global.response.ApiError;
import com.itting.interest.domain.InterestCategory;
import com.itting.interest.repository.InterestCategoryRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * 글을 쓰고 고치고 지우고, 조회수를 올린다({@code docs/api/08-community.md}).
 *
 * <p>글에 모임을 연결하는 기능은 모임 테이블이 생긴 뒤 08 커뮤니티 ③(KAN-53)에서 더한다. 그 전에는 연결할 수 있는
 * 모임이 하나도 없으므로 {@code meetupId}가 오면 늘 {@code MEETUP_NOT_FOUND}다.
 */
@Service
public class PostService {

	private final PostRepository postRepository;

	private final PostVersionRepository postVersionRepository;

	private final PostInterestRepository postInterestRepository;

	private final PostImageRepository postImageRepository;

	private final UploadedImageRepository uploadedImageRepository;

	private final InterestCategoryRepository interestCategoryRepository;

	private final SpringValidatorAdapter validator;

	private final Clock clock;

	public PostService(PostRepository postRepository, PostVersionRepository postVersionRepository,
			PostInterestRepository postInterestRepository, PostImageRepository postImageRepository,
			UploadedImageRepository uploadedImageRepository, InterestCategoryRepository interestCategoryRepository,
			Validator validator, Clock clock) {
		this.postRepository = postRepository;
		this.postVersionRepository = postVersionRepository;
		this.postInterestRepository = postInterestRepository;
		this.postImageRepository = postImageRepository;
		this.uploadedImageRepository = uploadedImageRepository;
		this.interestCategoryRepository = interestCategoryRepository;
		this.validator = new SpringValidatorAdapter(validator);
		this.clock = clock;
	}

	/**
	 * 1. 입력(모양, 없는 대분류, 쓸 수 없는 사진) → 2. 모임 → 3. 글 · 버전 1 · 태그 · 사진을 한 번에 저장.
	 */
	@Transactional
	public PostCreateResponse create(Long userId, PostCreateRequest request) {
		validate(userId, null, request);
		assertMeetupLinkable(request);
		Post post = postRepository.save(Post.create(userId, request.category()));
		postVersionRepository.save(PostVersion.create(post.getId(), 1, request.title(), request.content()));
		attachInterests(post.getId(), request.interestCategoryIds());
		attachImages(post.getId(), request.imageIds());
		postRepository.flush();
		return new PostCreateResponse(post.getId());
	}

	/**
	 * 1. 글 찾기(잠금) → 2. 작성자 → 3. 가려짐 → 4. 입력 → 5. 모임(모임이나 카테고리를 바꿨을 때만)
	 * → 6. 제목이나 본문이 달라졌으면 새 버전 → 7. 카테고리 · 태그 · 사진을 보낸 값으로 맞춤.
	 */
	@Transactional
	public void update(Long userId, Long postId, PostUpdateRequest request) {
		Post post = postRepository.findByIdForUpdate(postId)
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
		if (!post.isWrittenBy(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		if (post.isHidden()) {
			throw new BusinessException(CommunityErrorCode.POST_EDIT_NOT_ALLOWED);
		}
		validate(userId, postId, request);
		// 지금 연결한 모임은 늘 없으므로, 보낸 모임이 있으면 모임을 바꾼 것이다.
		assertMeetupLinkable(request);

		PostVersion latest = postVersionRepository.findTopByPostIdOrderByVersionDesc(postId)
				.orElseThrow(() -> new IllegalStateException("버전이 없는 글: " + postId));
		post.changeCategory(request.category());
		// 아래 태그 · 사진 떼기가 영속성 컨텍스트를 비우므로, 엔티티를 고치는 일은 여기까지 마친다.
		postInterestRepository.deleteAllOfPost(postId);
		postImageRepository.deleteAllOfPost(postId);
		if (latest.differsFrom(request.title(), request.content())) {
			postVersionRepository.save(PostVersion.create(postId, latest.getVersion() + 1, request.title(),
					request.content()));
		}
		attachInterests(postId, request.interestCategoryIds());
		attachImages(postId, request.imageIds());
		postRepository.flush();
	}

	/** 글을 지우고 달린 댓글 · 답글도 지운다. 가려진 글도 지울 수 있다. 좋아요 · 저장 줄은 남긴다. */
	@Transactional
	public void delete(Long userId, Long postId) {
		Post post = postRepository.findByIdForUpdate(postId)
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
		if (!post.isWrittenBy(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		post.delete(now);
		postRepository.deleteCommentsOf(postId, now);
	}

	/** 조회수를 1 올린다. 열 때마다 세고, 작성자가 열어도 센다. 가려진 글은 작성자에게도 {@code POST_HIDDEN}이다. */
	@Transactional
	public PostViewResponse increaseViewCount(Long postId) {
		Post post = postRepository.findById(postId)
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
		if (post.isHidden()) {
			throw new BusinessException(CommunityErrorCode.POST_HIDDEN);
		}
		postRepository.incrementViewCount(postId);
		return new PostViewResponse(postRepository.findViewCount(postId));
	}

	private void attachInterests(Long postId, List<Long> interestCategoryIds) {
		postInterestRepository.saveAll(interestCategoryIds.stream()
				.map(interestCategoryId -> PostInterest.create(postId, interestCategoryId))
				.toList());
	}

	private void attachImages(Long postId, List<Long> imageIds) {
		List<PostImage> images = new ArrayList<>();
		for (int i = 0; i < imageIds.size(); i++) {
			images.add(PostImage.create(postId, i + 1, imageIds.get(i)));
		}
		postImageRepository.saveAll(images);
	}

	/** 모임 테이블이 생기기 전이라 연결할 수 있는 모임이 없다. 모임을 꼭 연결해야 하는 카테고리는 입력 검사에서 막았다. */
	private void assertMeetupLinkable(PostInput input) {
		if (input.linkedMeetupId() != null) {
			throw new BusinessException(CommunityErrorCode.MEETUP_NOT_FOUND);
		}
	}

	/**
	 * Bean Validation, 카테고리와 모임, 대분류, 사진을 보고 어긋난 것을 {@code fields}에 모두 담는다.
	 *
	 * @param postId 고치는 글. 이미 이 글에 붙은 사진은 다시 보내도 된다. 새 글이면 null
	 */
	private void validate(Long userId, Long postId, PostInput input) {
		Errors errors = new BeanPropertyBindingResult(input, "request");
		validator.validate(input, errors);
		List<ApiError.FieldError> fields = new ArrayList<>();
		errors.getFieldErrors().forEach(error -> fields.add(new ApiError.FieldError(error.getField(),
				error.getDefaultMessage())));
		Set<String> invalid = fields.stream().map(ApiError.FieldError::field).collect(Collectors.toSet());

		if (input.category() != null && input.category().requiresMeetup() && input.linkedMeetupId() == null
				&& !invalid.contains("meetupId")) {
			fields.add(new ApiError.FieldError("meetupId", "모임 홍보와 모임 후기는 모임을 연결해야 합니다."));
		}
		if (!hasInvalid(invalid, "interestCategoryIds") && !interestCategoriesExist(input.interestCategoryIds())) {
			fields.add(new ApiError.FieldError("interestCategoryIds", "없는 관심사가 있습니다."));
		}
		if (!hasInvalid(invalid, "imageIds") && !imagesUsable(userId, postId, input.imageIds())) {
			fields.add(new ApiError.FieldError("imageIds", "붙일 수 없는 사진이 있습니다."));
		}
		if (!fields.isEmpty()) {
			fields.sort(Comparator.comparing(ApiError.FieldError::field));
			throw BusinessException.invalidInput(fields);
		}
	}

	/** 목록이 비었거나, 목록 자체나 그 안의 값({@code imageIds[0]})이 어긋났는지. */
	private static boolean hasInvalid(Set<String> invalid, String field) {
		return invalid.stream().anyMatch(name -> name.equals(field) || name.startsWith(field + "["));
	}

	private boolean interestCategoriesExist(List<Long> ids) {
		if (ids.isEmpty()) {
			return true;
		}
		long found = interestCategoryRepository.findAllById(ids).stream()
				.filter(category -> category.getDeletedAt() == null)
				.map(InterestCategory::getId)
				.distinct()
				.count();
		return found == ids.size();
	}

	/** 모두 있는 사진이고, 내가 올렸고, 다른 글에 붙지 않았는지. */
	private boolean imagesUsable(Long userId, Long postId, List<Long> ids) {
		if (ids.isEmpty()) {
			return true;
		}
		Map<Long, UploadedImage> images = uploadedImageRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(UploadedImage::getId, Function.identity()));
		if (images.size() != ids.size()
				|| images.values().stream().anyMatch(image -> !image.getUploaderId().equals(userId))) {
			return false;
		}
		Set<Long> attachedElsewhere = new HashSet<>();
		postImageRepository.findAllByImageIdIn(ids).stream()
				.filter(attached -> !attached.getPostId().equals(postId))
				.forEach(attached -> attachedElsewhere.add(attached.getImageId()));
		return attachedElsewhere.isEmpty();
	}

}
