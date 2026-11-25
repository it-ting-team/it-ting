package com.itting.community.domain;

/**
 * 올리는 사진의 규칙({@code docs/api/08-community.md}의 사진 올리기).
 */
public final class ImageRules {

	/** 한 장의 최대 크기. {@code spring.servlet.multipart.max-file-size}와 같다. */
	public static final long MAX_BYTES = 5L * 1024 * 1024;

	public static final String MISSING_MESSAGE = "사진을 골라 주세요.";

	public static final String FORMAT_MESSAGE = "JPEG, PNG, WebP 사진만 올릴 수 있습니다.";

	public static final String SIZE_MESSAGE = "5MB 이하의 사진만 올릴 수 있습니다.";

	private ImageRules() {
	}

}
