package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/images}의 {@code data}.
 */
public record ImageUploadResponse(
		@Schema(description = "올린 사진의 id. 글이나 메시지의 imageIds에 담는다", example = "512") Long imageId,
		@Schema(description = "사진의 주소. 썸네일을 그리는 데 쓴다",
				example = "http://localhost:8080/images/2026/11/0b9f2c4e-3d1a-4f0e-9a51-6f1c2d7e8a90.webp") String url) {
}
