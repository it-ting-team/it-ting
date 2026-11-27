package com.itting.community.service;

import com.itting.community.repository.CommentLikeRepository;
import com.itting.community.repository.PostBookmarkRepository;
import com.itting.community.repository.PostLikeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴의 커뮤니티 영역 정리({@code docs/api/10-settings.md}의 "탈퇴할 때 하는 일"). 탈퇴 트랜잭션 안에서만 부른다.
 * 글 좋아요, 댓글 좋아요, 저장을 지운다. 내가 쓴 글과 댓글, 내가 넣은 신고는 남긴다.
 */
@Component
public class CommunityWithdrawalCleaner {

	private final PostLikeRepository postLikes;

	private final CommentLikeRepository commentLikes;

	private final PostBookmarkRepository bookmarks;

	public CommunityWithdrawalCleaner(PostLikeRepository postLikes, CommentLikeRepository commentLikes,
			PostBookmarkRepository bookmarks) {
		this.postLikes = postLikes;
		this.commentLikes = commentLikes;
		this.bookmarks = bookmarks;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void clean(Long userId) {
		postLikes.deleteAllByUserId(userId);
		commentLikes.deleteAllByUserId(userId);
		bookmarks.deleteAllByUserId(userId);
	}

}
