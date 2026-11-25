package com.itting.notification.domain;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 브라우저 푸시를 받을 브라우저 하나({@code push_subscription}). "브라우저 알림 받기"를 켠 브라우저마다 한 줄이다.
 * 브라우저 하나는 한 계정의 푸시만 받으므로 {@code endpoint}는 겹치지 않고, 같은 주소를 다른 계정이 등록하면
 * 새 줄 없이 {@link #reassign}으로 주인과 키를 바꾼다.
 */
@Entity
@Table(name = "push_subscription")
public class PushSubscription extends BaseCreatedTimeEntity {

	/** 주소의 최대 길이. 칼럼의 길이이고, 이 길이까지 UNIQUE 색인이 걸린다. */
	public static final int ENDPOINT_MAX = 2000;

	/** 키 칼럼의 최대 길이. */
	public static final int KEY_MAX = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 브라우저가 준 푸시 수신 주소. 푸시 서비스(FCM 등)의 주소이고 길이 · 모양은 서비스가 정한다. */
	@Column(nullable = false, length = ENDPOINT_MAX)
	private String endpoint;

	/** 브라우저의 공개 키(base64url). 푸시 내용을 암호화할 때 쓴다. */
	@Column(nullable = false, length = KEY_MAX)
	private String p256dh;

	/** 브라우저의 인증 값(base64url). 푸시 내용을 암호화할 때 쓴다. */
	@Column(nullable = false, length = KEY_MAX)
	private String auth;

	protected PushSubscription() {
	}

	public static PushSubscription create(Long userId, String endpoint, String p256dh, String auth) {
		PushSubscription subscription = new PushSubscription();
		subscription.userId = userId;
		subscription.endpoint = endpoint;
		subscription.p256dh = p256dh;
		subscription.auth = auth;
		return subscription;
	}

	/** 이미 등록된 브라우저를 다시 등록한다. 주인을 바꾸고 키를 새로 받은 값으로 바꾼다. */
	public void reassign(Long userId, String p256dh, String auth) {
		this.userId = userId;
		this.p256dh = p256dh;
		this.auth = auth;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public String getP256dh() {
		return p256dh;
	}

	public String getAuth() {
		return auth;
	}

}
