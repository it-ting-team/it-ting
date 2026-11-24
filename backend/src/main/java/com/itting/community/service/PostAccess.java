package com.itting.community.service;

import java.util.Optional;

import com.itting.community.domain.CommunityErrorCode;
import com.itting.community.domain.Post;
import com.itting.community.repository.PostRepository;
import com.itting.global.error.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 댓글 · 좋아요 · 저장이 글을 찾을 때 지키는 규칙({@code docs/api/08-community.md}의 글과 댓글의 상태).
 * 없거나 삭제된 글은 {@code POST_NOT_FOUND}, 가려진 글은 {@code POST_HIDDEN}이다. 부르는 쪽의 트랜잭션 안에서 쓴다.
 */
@Component
public class PostAccess {

	private final PostRepository postRepository;

	public PostAccess(PostRepository postRepository) {
		this.postRepository = postRepository;
	}

	/** 좋아요 · 저장을 할 수 있는 글. 공개 글만이고, 가려진 글은 작성자에게도 {@code POST_HIDDEN}이다. */
	Post findPublic(Long postId) {
		return requirePublic(postRepository.findById(postId));
	}

	/**
	 * 댓글을 쓸 수 있는 글. {@link #findPublic}과 같고, 글을 잠가(공유) 글을 지우는 트랜잭션과 순서가 정해지게 한다.
	 */
	Post findPublicForShare(Long postId) {
		return requirePublic(postRepository.findByIdForShare(postId));
	}

	/** 댓글 목록을 볼 수 있는 글. 공개 글이고, 가려진 글은 작성자만 볼 수 있다. */
	Post findReadable(Long userId, Long postId) {
		Post post = postRepository.findById(postId)
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
		if (post.isHidden() && !post.isWrittenBy(userId)) {
			throw new BusinessException(CommunityErrorCode.POST_HIDDEN);
		}
		return post;
	}

	private static Post requirePublic(Optional<Post> found) {
		Post post = found
				.filter(candidate -> !candidate.isDeleted())
				.orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
		if (post.isHidden()) {
			throw new BusinessException(CommunityErrorCode.POST_HIDDEN);
		}
		return post;
	}

}
