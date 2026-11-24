package com.itting.notification.service;

import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.notification.domain.NotificationCategory;
import com.itting.notification.domain.NotificationSetting;
import com.itting.notification.dto.NotificationSettingResponse;
import com.itting.notification.dto.NotificationSettingUpdateRequest;
import com.itting.notification.dto.NotificationSettingUpdateResponse;
import com.itting.notification.repository.NotificationSettingRepository;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 갈래별 알림 켜고 끄기를 읽고 바꾼다({@code /api/v1/me/notification-settings}). 줄이 없는 계정은 모두 켠 것으로 보고,
 * 처음 바꿀 때 줄을 만든다. 이미 받은 알림은 지우지 않는다.
 */
@Service
@Transactional
public class NotificationSettingService {

	private final NotificationSettingRepository notificationSettingRepository;

	private final UserRepository userRepository;

	private final NotificationQueryService notificationQueryService;

	private final NotificationEventPublisher events;

	/** 푸시를 보낼 수 없으면(세 설정 가운데 하나라도 비었거나 키가 틀림) null. */
	private final String pushPublicKey;

	public NotificationSettingService(NotificationSettingRepository notificationSettingRepository,
			UserRepository userRepository, NotificationQueryService notificationQueryService,
			NotificationEventPublisher events, WebPushSender pushSender) {
		this.notificationSettingRepository = notificationSettingRepository;
		this.userRepository = userRepository;
		this.notificationQueryService = notificationQueryService;
		this.events = events;
		this.pushPublicKey = pushSender.publicKey();
	}

	/** 내 설정. 줄이 없으면 다섯 값을 모두 {@code true}로 준다. 푸시 공개 키는 설정에서 읽는다. */
	@Transactional(readOnly = true)
	public NotificationSettingResponse getSettings(Long userId) {
		NotificationSetting setting = notificationSettingRepository.findById(userId).orElse(null);
		return new NotificationSettingResponse(isOn(setting, NotificationCategory.MEETUP),
				isOn(setting, NotificationCategory.CHAT), isOn(setting, NotificationCategory.COMMUNITY),
				isOn(setting, NotificationCategory.BURROW), isOn(setting, NotificationCategory.NOTICE),
				pushPublicKey);
	}

	/**
	 * 다섯 값을 모두 바꾼다. 줄이 없으면 만든다. 바꾸기 전과 뒤의 빨간 점이 다르면 커밋 뒤 실시간 소식을 낸다
	 * (안 읽은 채팅 알림이 있는데 채팅 알림을 끄면 빨간 점이 꺼지는 경우).
	 *
	 * <p>같은 계정의 요청이 겹쳐도 줄이 두 번 만들어지거나 빨간 점이 어긋나지 않게 계정 행을 잠그고 시작한다.
	 *
	 * @throws BusinessException 계정이 없거나 탈퇴했는데 세션만 남았으면 {@code UNAUTHENTICATED}
	 */
	public NotificationSettingUpdateResponse updateSettings(Long userId, NotificationSettingUpdateRequest body) {
		userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));

		boolean hadUnread = notificationQueryService.unreadCount(userId).hasUnread();
		NotificationSetting setting = notificationSettingRepository.findById(userId).orElse(null);
		if (setting == null) {
			setting = NotificationSetting.create(userId, body.isMeetupOn(), body.isChatOn(), body.isCommunityOn(),
					body.isBurrowOn(), body.isNoticeOn());
		}
		else {
			setting.update(body.isMeetupOn(), body.isChatOn(), body.isCommunityOn(), body.isBurrowOn(),
					body.isNoticeOn());
		}
		// 바꾼 값을 DB에 먼저 내보내야 아래에서 빨간 점을 다시 계산할 때 반영된다.
		notificationSettingRepository.saveAndFlush(setting);

		if (notificationQueryService.unreadCount(userId).hasUnread() != hadUnread) {
			events.unreadChanged(userId);
		}
		return new NotificationSettingUpdateResponse(body.isMeetupOn(), body.isChatOn(), body.isCommunityOn(),
				body.isBurrowOn(), body.isNoticeOn());
	}

	private static boolean isOn(NotificationSetting setting, NotificationCategory category) {
		return setting == null || setting.isOn(category);
	}

}
