package com.itting.community.repository;

import java.time.Instant;

import com.itting.community.domain.PostBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostBookmarkRepository extends JpaRepository<PostBookmark, PostBookmark.Key> {

	/**
	 * 이미 저장한 글이 아닐 때만 줄을 만든다. 같은 사람이 동시에 두 번 눌러도 한 줄만 생기고 오류가 나지 않는다.
	 *
	 * @return 새로 만들었으면 1, 이미 있었으면 0
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "INSERT IGNORE INTO post_bookmark (post_id, user_id, created_at) VALUES (:postId, :userId, :now)",
			nativeQuery = true)
	int insertIfAbsent(@Param("postId") Long postId, @Param("userId") Long userId, @Param("now") Instant now);

	/** 내 줄을 지운다. 없으면 아무것도 하지 않는다. */
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM PostBookmark b WHERE b.postId = :postId AND b.userId = :userId")
	int deleteMine(@Param("postId") Long postId, @Param("userId") Long userId);

}
