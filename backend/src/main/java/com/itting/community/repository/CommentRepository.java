package com.itting.community.repository;

import java.time.Instant;
import java.util.Optional;

import com.itting.community.domain.Comment;
import com.itting.community.domain.PostStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	/** 댓글을 트랜잭션이 끝날 때까지 잠근다. 같은 댓글을 동시에 고칠 때 버전 번호가 겹치지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Comment c WHERE c.id = :id")
	Optional<Comment> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 댓글 줄을 잠그고 지금 상태를 읽는다. 답글을 쓸 때 맨 위 댓글을 잠가, 그 댓글을 지우는 트랜잭션과 순서가 정해지게 한다
	 * (지워지는 댓글에 답글이 새로 달리지 않는다). 엔티티를 읽지 않으므로 앞서 읽은 값이 남아 있지 않다.
	 *
	 * @return {@code PUBLIC}, {@code HIDDEN}, {@code DELETED}. 없는 댓글이면 비어 있다
	 */
	@Query(value = "SELECT status FROM comment WHERE id = :id FOR UPDATE", nativeQuery = true)
	Optional<String> lockStatus(@Param("id") Long id);

	/** 맨 위 댓글에 달린 답글 가운데 삭제되지 않은 것을 모두 삭제한다. 댓글을 지울 때 함께 부른다. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			UPDATE comment SET status = 'DELETED', deleted_at = :now, updated_at = :now
			WHERE parent_id = :parentId AND status <> 'DELETED'
			""", nativeQuery = true)
	int deleteRepliesOf(@Param("parentId") Long parentId, @Param("now") Instant now);

	/** 글의 댓글과 답글 가운데 상태가 {@code status}가 아닌 것의 수. 삭제되지 않은 것을 셀 때 {@code DELETED}를 넘긴다. */
	long countByPostIdAndStatusNot(Long postId, PostStatus status);

}
