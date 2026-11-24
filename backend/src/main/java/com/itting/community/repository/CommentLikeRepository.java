package com.itting.community.repository;

import java.time.Instant;

import com.itting.community.domain.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLike.Key> {

	/**
	 * 이미 누른 좋아요가 없을 때만 줄을 만든다. 같은 사람이 동시에 두 번 눌러도 한 줄만 생기고 오류가 나지 않는다.
	 *
	 * @return 새로 만들었으면 1, 이미 있었으면 0
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "INSERT IGNORE INTO comment_like (comment_id, user_id, created_at) VALUES (:commentId, :userId, :now)",
			nativeQuery = true)
	int insertIfAbsent(@Param("commentId") Long commentId, @Param("userId") Long userId, @Param("now") Instant now);

	/** 내 줄을 지운다. 없으면 아무것도 하지 않는다. */
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM CommentLike l WHERE l.commentId = :commentId AND l.userId = :userId")
	int deleteMine(@Param("commentId") Long commentId, @Param("userId") Long userId);

	long countByCommentId(Long commentId);

}
