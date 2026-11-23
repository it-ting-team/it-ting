package com.itting.community.repository;

import java.util.Collection;
import java.util.List;

import com.itting.community.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

	/**
	 * 글의 사진을 모두 뗀다. 올린 사진({@code uploaded_image})은 남긴다.
	 * 바로 실행하고 영속성 컨텍스트를 비우므로 이어서 같은 사진을 다른 순서로 붙여도 된다.
	 * 비우면 앞서 읽은 엔티티가 떨어지므로 엔티티를 고치는 일은 이 앞에서 마친다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM PostImage i WHERE i.postId = :postId")
	void deleteAllOfPost(@Param("postId") Long postId);

	/** 이 사진들 가운데 글에 붙은 것. */
	List<PostImage> findAllByImageIdIn(Collection<Long> imageIds);

}
