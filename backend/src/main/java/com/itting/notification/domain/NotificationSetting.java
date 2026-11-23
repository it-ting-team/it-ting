package com.itting.notification.domain;

import java.util.EnumSet;
import java.util.Set;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 갈래별 알림 켜고 끄기({@code notification_setting}). 줄이 없는 계정은 모두 켠 것으로 본다({@link #ALL_ON}).
 * 바꾸는 API는 10 설정에 있다.
 */
@Entity
@Table(name = "notification_setting")
public class NotificationSetting extends BaseTimeEntity {

	/** 줄이 없는 계정이 켜 둔 갈래. */
	public static final Set<NotificationCategory> ALL_ON = EnumSet.allOf(NotificationCategory.class);

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "meetup_on", nullable = false)
	private boolean meetupOn = true;

	@Column(name = "chat_on", nullable = false)
	private boolean chatOn = true;

	@Column(name = "community_on", nullable = false)
	private boolean communityOn = true;

	@Column(name = "burrow_on", nullable = false)
	private boolean burrowOn = true;

	@Column(name = "notice_on", nullable = false)
	private boolean noticeOn = true;

	protected NotificationSetting() {
	}

	public boolean isOn(NotificationCategory category) {
		return switch (category) {
			case MEETUP -> meetupOn;
			case CHAT -> chatOn;
			case COMMUNITY -> communityOn;
			case BURROW -> burrowOn;
			case NOTICE -> noticeOn;
		};
	}

	/** 켜 둔 갈래. */
	public Set<NotificationCategory> enabledCategories() {
		Set<NotificationCategory> categories = EnumSet.noneOf(NotificationCategory.class);
		for (NotificationCategory category : NotificationCategory.values()) {
			if (isOn(category)) {
				categories.add(category);
			}
		}
		return categories;
	}

	public Long getUserId() {
		return userId;
	}

}
