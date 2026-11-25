package com.itting.community.service.image;

import java.util.Optional;

/**
 * 올릴 수 있는 사진의 형식. 파일 이름이 아니라 파일 앞부분의 바이트로 알아낸다({@code docs/api-convention.md}의 파일 올리기).
 */
public enum ImageFormat {

	JPEG("jpg"),
	PNG("png"),
	WEBP("webp");

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private final String extension;

	ImageFormat(String extension) {
		this.extension = extension;
	}

	/** 저장할 파일의 확장자. */
	public String extension() {
		return extension;
	}

	/** @return JPEG · PNG · WebP가 아니면 비어 있다 */
	public static Optional<ImageFormat> detect(byte[] bytes) {
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
			return Optional.of(JPEG);
		}
		if (startsWith(bytes, 0, PNG_SIGNATURE)) {
			return Optional.of(PNG);
		}
		if (startsWith(bytes, 0, new byte[] { 'R', 'I', 'F', 'F' }) && startsWith(bytes, 8, new byte[] { 'W', 'E', 'B', 'P' })) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
		if (bytes.length < offset + prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (bytes[offset + i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

}
