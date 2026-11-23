package com.itting.auth.domain;

/**
 * 로그인하거나 계정을 만든 뒤 프론트엔드가 갈 화면({@code docs/api/02-auth.md}의 "다음에 갈 화면").
 * 저장하지 않고 계정 상태에서 계산한다({@code NextStepService}).
 */
public enum NextStep {

	/** 온보딩을 마치지 않았다. 온보딩 1로 간다. */
	ONBOARDING,

	/** 온보딩을 마쳤지만 이번 학기의 캠퍼스 · 층을 고르지 않았다. 내 땅굴 위에 새 학기 안내를 띄운다. */
	NEW_TERM,

	/** 내 땅굴로 간다. */
	HOME

}
