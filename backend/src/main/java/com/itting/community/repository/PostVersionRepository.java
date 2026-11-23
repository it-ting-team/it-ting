package com.itting.community.repository;

import java.util.Optional;

import com.itting.community.domain.PostVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostVersionRepository extends JpaRepository<PostVersion, Long> {

	/** 지금 보이는 버전(번호가 가장 큰 것). */
	Optional<PostVersion> findTopByPostIdOrderByVersionDesc(Long postId);

}
