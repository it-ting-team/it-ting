package com.itting.notification.repository;

import com.itting.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

	/** 이 사람의 알림 설정을 지운다(회원 탈퇴). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM NotificationSetting s WHERE s.userId = :userId")
	int deleteByUserIdNow(@Param("userId") Long userId);

}
