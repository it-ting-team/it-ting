package com.itting.notification.service;

import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.notification.domain.PushSubscription;
import com.itting.notification.dto.PushSubscriptionRequest;
import com.itting.notification.repository.PushSubscriptionRepository;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 브라우저를 푸시를 받을 곳으로 등록하고 해제한다({@code /api/v1/me/push-subscriptions}).
 * 브라우저 하나는 한 계정의 푸시만 받으므로, 이미 있는 주소를 등록하면 새 줄 없이 주인과 키를 바꾼다.
 */
@Service
public class PushSubscriptionService {

	/** 같은 주소가 동시에 등록돼 부딪혔을 때 다시 해 보는 횟수(처음 시도 포함). 두 번째는 먼저 들어간 줄을 바꾼다. */
	private static final int MAX_ATTEMPTS = 3;

	private final PushSubscriptionRepository pushSubscriptionRepository;

	private final UserRepository userRepository;

	private final TransactionTemplate transactionTemplate;

	public PushSubscriptionService(PushSubscriptionRepository pushSubscriptionRepository,
			UserRepository userRepository, TransactionTemplate transactionTemplate) {
		this.pushSubscriptionRepository = pushSubscriptionRepository;
		this.userRepository = userRepository;
		this.transactionTemplate = transactionTemplate;
	}

	/**
	 * 이 브라우저를 등록한다. 같은 주소가 없으면 새 줄을 넣고, 있으면 그 줄의 주인을 나로, 키를 보낸 값으로 바꾼다.
	 *
	 * <p>같은 주소가 동시에 들어와도 오류가 나지 않는다. 먼저 들어간 요청이 줄을 만들고({@code created}), 겹친 요청은
	 * UNIQUE에 걸려 되돌려진 뒤 새 트랜잭션에서 그 줄을 바꾼다. 없는 줄은 잠글 수 없어, 잠그고 읽는 방법은 두 요청이 모두
	 * 넣으려다 교착이 날 수 있어 쓰지 않았다.
	 *
	 * @throws BusinessException 계정이 없거나 탈퇴했는데 세션만 남았으면 {@code UNAUTHENTICATED}
	 */
	public Registration register(Long userId, PushSubscriptionRequest request) {
		for (int attempt = 1;; attempt++) {
			try {
				return transactionTemplate.execute(status -> registerOnce(userId, request));
			}
			catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
				if (attempt >= MAX_ATTEMPTS) {
					throw e;
				}
			}
		}
	}

	/**
	 * 내 등록 하나를 지운다. 없거나 남의 것이면 아무것도 지우지 않고 지나간다(성공과 같다).
	 */
	public void unregister(Long userId, Long pushSubscriptionId) {
		transactionTemplate.executeWithoutResult(
				status -> pushSubscriptionRepository.deleteByIdAndUserId(pushSubscriptionId, userId));
	}

	/** 푸시 서비스가 없는 구독이라고 답한 줄을 지운다. 푸시를 보내다 알게 된 줄이라 누구의 것인지는 따지지 않는다. */
	void remove(Long pushSubscriptionId) {
		transactionTemplate.executeWithoutResult(
				status -> pushSubscriptionRepository.deleteBySubscriptionId(pushSubscriptionId));
	}

	private Registration registerOnce(Long userId, PushSubscriptionRequest request) {
		userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
		String endpoint = request.endpoint();
		String p256dh = request.keys().p256dh();
		String auth = request.keys().auth();
		PushSubscription existing = pushSubscriptionRepository.findByEndpoint(endpoint).orElse(null);
		if (existing != null) {
			existing.reassign(userId, p256dh, auth);
			pushSubscriptionRepository.saveAndFlush(existing);
			return new Registration(existing.getId(), false);
		}
		PushSubscription created = pushSubscriptionRepository
				.saveAndFlush(PushSubscription.create(userId, endpoint, p256dh, auth));
		return new Registration(created.getId(), true);
	}

	/**
	 * @param pushSubscriptionId 등록의 id
	 * @param created 새로 만든 줄이면 true, 이미 있던 줄을 바꿨으면 false
	 */
	public record Registration(Long pushSubscriptionId, boolean created) {
	}

}
