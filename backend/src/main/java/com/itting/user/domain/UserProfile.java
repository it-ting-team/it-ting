package com.itting.user.domain;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 다른 사용자에게 보여 주는 프로필({@code user_profile}). 계정을 만들 때 함께 만들고, 탈퇴하면 지운다.
 * id는 계정 id와 같다.
 */
@Entity
@Table(name = "user_profile")
public class UserProfile extends BaseTimeEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	/** 온보딩을 마칠 때 정한다. 그 전에는 비어 있다. */
	@Column(length = 12)
	private String nickname;

	/** SSAFY 인증에 쓴 기수. 바꿀 수 없다. */
	@Column(nullable = false)
	private short generation;

	@Column(name = "campus_floor_id", nullable = false)
	private Long campusFloorId;

	@Column(length = 150)
	private String intro;

	@Column(name = "profile_image_url", length = 500)
	private String profileImageUrl;

	@Column(name = "mole_head", length = 30)
	private String moleHead;

	@Column(name = "mole_face", length = 30)
	private String moleFace;

	@Column(name = "mole_neck", length = 30)
	private String moleNeck;

	@Column(name = "mole_hand", length = 30)
	private String moleHand;

	protected UserProfile() {
	}

	/** 계정을 만들 때 함께 만드는 프로필. 닉네임과 두더지 아이템은 온보딩을 마칠 때 정한다. */
	public static UserProfile create(Long userId, int generation, Long campusFloorId) {
		UserProfile profile = new UserProfile();
		profile.userId = userId;
		profile.generation = (short) generation;
		profile.campusFloorId = campusFloorId;
		return profile;
	}

	public Long getUserId() {
		return userId;
	}

	public String getNickname() {
		return nickname;
	}

	public int getGeneration() {
		return generation;
	}

	public Long getCampusFloorId() {
		return campusFloorId;
	}

	public String getIntro() {
		return intro;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}

	public String getMoleHead() {
		return moleHead;
	}

	public String getMoleFace() {
		return moleFace;
	}

	public String getMoleNeck() {
		return moleNeck;
	}

	public String getMoleHand() {
		return moleHand;
	}

}
