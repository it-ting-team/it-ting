package com.itting.notification.repository;

import java.util.List;
import java.util.Optional;

import com.itting.notification.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 푸시를 받을 브라우저.
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	/** 같은 주소의 줄. 주소는 겹치지 않는다. */
	Optional<PushSubscription> findByEndpoint(String endpoint);

	/** 그 사람이 등록한 모든 브라우저. */
	List<PushSubscription> findByUserId(Long userId);

	/**
	 * 내 줄만 지운다. 없거나 남의 줄이면 아무것도 지우지 않는다.
	 *
	 * @return 지운 줄 수
	 */
	@Modifying
	@Query("DELETE FROM PushSubscription s WHERE s.id = :id AND s.userId = :userId")
	int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	/**
	 * 푸시 서비스가 없는 구독이라고 답한 줄을 지운다. 누구의 줄인지는 가리지 않는다.
	 *
	 * @return 지운 줄 수
	 */
	@Modifying
	@Query("DELETE FROM PushSubscription s WHERE s.id = :id")
	int deleteBySubscriptionId(@Param("id") Long id);

	/** 이 사람의 브라우저 푸시 등록을 모두 지운다(회원 탈퇴). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM PushSubscription p WHERE p.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);

}
