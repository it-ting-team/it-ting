package com.itting.community.repository;

import com.itting.community.domain.PostInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostInterestRepository extends JpaRepository<PostInterest, PostInterest.Key> {

	/**
	 * 글의 태그를 모두 뗀다. 바로 실행하고 영속성 컨텍스트를 비우므로 이어서 같은 태그를 다시 붙여도 된다.
	 * 비우면 앞서 읽은 엔티티가 떨어지므로 엔티티를 고치는 일은 이 앞에서 마친다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM PostInterest i WHERE i.postId = :postId")
	void deleteAllOfPost(@Param("postId") Long postId);

}
