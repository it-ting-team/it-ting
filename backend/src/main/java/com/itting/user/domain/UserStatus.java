package com.itting.user.domain;

/** 계정 상태({@code user.status}). 온보딩을 마쳤는지는 {@code onboarded_at}으로 안다. */
public enum UserStatus {

	ACTIVE,
	WITHDRAWN

}
