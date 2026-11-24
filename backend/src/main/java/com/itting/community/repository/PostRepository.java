package com.itting.community.repository;

import java.time.Instant;
import java.util.Optional;

import com.itting.community.domain.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

	/** 글을 트랜잭션이 끝날 때까지 잠근다. 같은 글을 동시에 고칠 때 버전 번호가 겹치지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Post p WHERE p.id = :id")
	Optional<Post> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 글을 트랜잭션이 끝날 때까지 다른 트랜잭션이 바꾸지 못하게 한다(공유 잠금). 글을 지우는 트랜잭션({@link #findByIdForUpdate})과
	 * 순서가 정해지므로, 지워지는 글에 댓글이 새로 달리지 않는다. 댓글을 쓸 때 쓴다.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("SELECT p FROM Post p WHERE p.id = :id")
	Optional<Post> findByIdForShare(@Param("id") Long id);

	/** 조회수에 1을 더한다. 읽고 쓰지 않고 SQL 한 번으로 더해 동시에 열어도 빠지지 않는다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE post SET view_count = view_count + 1 WHERE id = :id", nativeQuery = true)
	int incrementViewCount(@Param("id") Long id);

	@Query(value = "SELECT view_count FROM post WHERE id = :id", nativeQuery = true)
	int findViewCount(@Param("id") Long id);

	/** 글의 댓글과 답글 가운데 삭제되지 않은 것을 모두 삭제한다. 글을 지울 때 함께 부른다. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			UPDATE comment SET status = 'DELETED', deleted_at = :now, updated_at = :now
			WHERE post_id = :postId AND status <> 'DELETED'
			""", nativeQuery = true)
	int deleteCommentsOf(@Param("postId") Long postId, @Param("now") Instant now);

}
