package com.itting.user.domain;

import java.time.Instant;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 계정({@code user}). 로그인 수단, SSAFY 인증, 온보딩 완료와 탈퇴 상태를 둔다.
 * 다른 사람에게 보여 주는 값은 {@link UserProfile}에 있다.
 */
/*
 * 바꾼 칼럼만 UPDATE한다. 잠그지 않고 읽은 계정을 고쳐 저장해도, 그 사이 다른 트랜잭션이 바꾼 칼럼(예: Google 연동 해제)을
 * 읽은 때의 값으로 되돌리지 않는다.
 */
@DynamicUpdate
@Entity
@Table(name = "`user`")
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 이메일로 가입한 계정의 로그인 이메일. Google로만 가입했거나 탈퇴하면 비어 있다. */
	@Column(length = 254)
	private String email;

	/** 비밀번호의 BCrypt 해시. Google로만 가입했으면 비어 있다. */
	@Column(name = "password_hash", length = 100)
	private String passwordHash;

	@Column(name = "google_sub")
	private String googleSub;

	@Column(name = "google_email", length = 254)
	private String googleEmail;

	@Column(name = "mattermost_id", length = 64)
	private String mattermostId;

	@Column(name = "ssafy_verified_at", nullable = false)
	private Instant ssafyVerifiedAt;

	@Column(name = "terms_agreed_at", nullable = false)
	private Instant termsAgreedAt;

	/** 온보딩을 마친 시각. 비어 있으면 온보딩을 마치지 않았다. */
	@Column(name = "onboarded_at")
	private Instant onboardedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	protected User() {
	}

	/**
	 * 이메일 가입을 마친 계정. SSAFY 인증과 필수 동의를 마친 시각은 {@code now}다. 온보딩은 아직이다.
	 */
	public static User createByEmail(String email, String passwordHash, String mattermostId, Instant now) {
		User user = new User();
		user.email = email;
		user.passwordHash = passwordHash;
		user.mattermostId = mattermostId;
		user.ssafyVerifiedAt = now;
		user.termsAgreedAt = now;
		user.role = UserRole.USER;
		user.status = UserStatus.ACTIVE;
		return user;
	}

	/**
	 * Google 가입을 마친 계정. 이메일과 비밀번호는 비워 둔다. SSAFY 인증과 필수 동의를 마친 시각은 {@code now}다.
	 * 온보딩은 아직이다.
	 */
	public static User createByGoogle(String googleSub, String googleEmail, String mattermostId, Instant now) {
		User user = new User();
		user.googleSub = googleSub;
		user.googleEmail = googleEmail;
		user.mattermostId = mattermostId;
		user.ssafyVerifiedAt = now;
		user.termsAgreedAt = now;
		user.role = UserRole.USER;
		user.status = UserStatus.ACTIVE;
		return user;
	}

	/** Google 계정을 연결한다(설정의 Google 연동). */
	public void linkGoogle(String googleSub, String googleEmail) {
		this.googleSub = googleSub;
		this.googleEmail = googleEmail;
	}

	/** Google 연동을 해제한다. */
	public void unlinkGoogle() {
		this.googleSub = null;
		this.googleEmail = null;
	}

	/** 비밀번호를 바꾼다(비밀번호 재설정, 비밀번호 변경). */
	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	/** 온보딩을 마친다. 이 뒤로 {@link #isOnboarded()}가 참이다. */
	public void completeOnboarding(Instant now) {
		this.onboardedAt = now;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	public boolean isOnboarded() {
		return onboardedAt != null;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getGoogleSub() {
		return googleSub;
	}

	public String getGoogleEmail() {
		return googleEmail;
	}

	public String getMattermostId() {
		return mattermostId;
	}

	public Instant getSsafyVerifiedAt() {
		return ssafyVerifiedAt;
	}

	public Instant getTermsAgreedAt() {
		return termsAgreedAt;
	}

	public Instant getOnboardedAt() {
		return onboardedAt;
	}

	public UserRole getRole() {
		return role;
	}

	public UserStatus getStatus() {
		return status;
	}

}
