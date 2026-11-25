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

	/**
	 * 온보딩에서 정한 닉네임과 두더지 아이템을 넣는다. 아이템 코드가 null이면 그 부위는 "없음"이다.
	 */
	public void completeOnboarding(String nickname, String moleHead, String moleFace, String moleNeck,
			String moleHand) {
		this.nickname = nickname;
		this.moleHead = moleHead;
		this.moleFace = moleFace;
		this.moleNeck = moleNeck;
		this.moleHand = moleHand;
	}

	/** 꾸미기 모드에서 고른 두더지 아이템으로 바꾼다. null인 부위는 "없음"이다. */
	public void changeMole(String moleHead, String moleFace, String moleNeck, String moleHand) {
		this.moleHead = moleHead;
		this.moleFace = moleFace;
		this.moleNeck = moleNeck;
		this.moleHand = moleHand;
	}

	/**
	 * 내 정보 수정에서 바꾼 닉네임, 캠퍼스-층, 자기소개를 넣는다. 기수는 바꿀 수 없다.
	 *
	 * @param intro 자기소개. null이면 비운다
	 */
	public void changeProfile(String nickname, Long campusFloorId, String intro) {
		this.nickname = nickname;
		this.campusFloorId = campusFloorId;
		this.intro = intro;
	}

	/** 새 학기를 고를 때 프로필의 캠퍼스-층을 입구와 맞춘다. */
	public void moveToCampusFloor(Long campusFloorId) {
		this.campusFloorId = campusFloorId;
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
