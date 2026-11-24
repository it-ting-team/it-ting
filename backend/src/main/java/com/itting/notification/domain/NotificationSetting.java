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
 * 처음 바꿀 때 줄을 만든다({@link #create}). 바꾸는 API는 10 설정에 있다.
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

	/** 그 계정의 새 줄. 다섯 갈래의 켜고 끔을 모두 받는다. */
	public static NotificationSetting create(Long userId, boolean meetupOn, boolean chatOn, boolean communityOn,
			boolean burrowOn, boolean noticeOn) {
		NotificationSetting setting = new NotificationSetting();
		setting.userId = userId;
		setting.update(meetupOn, chatOn, communityOn, burrowOn, noticeOn);
		return setting;
	}

	/** 다섯 갈래의 켜고 끔을 모두 바꾼다. 이미 받은 알림은 건드리지 않는다. */
	public void update(boolean meetupOn, boolean chatOn, boolean communityOn, boolean burrowOn, boolean noticeOn) {
		this.meetupOn = meetupOn;
		this.chatOn = chatOn;
		this.communityOn = communityOn;
		this.burrowOn = burrowOn;
		this.noticeOn = noticeOn;
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
