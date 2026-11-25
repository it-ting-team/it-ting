package com.itting.community.controller;

import com.itting.community.dto.ImageUploadResponse;
import com.itting.community.service.ImageService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사진 올리기({@code docs/api/08-community.md}). 글쓰기 · 글 수정과 채팅 첨부가 함께 쓴다. 로그인이 필요하고, 온보딩과 이번 학기
 * 고르기를 마친 계정만 부른다.
 */
@Tag(name = "08 커뮤니티", description = "글 · 댓글 · 좋아요 · 저장")
@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

	private final ImageService imageService;

	public ImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@Operation(summary = "사진 올리기",
			description = "사진 한 장을 multipart/form-data의 file로 올린다. JPEG · PNG · WebP(파일 내용으로 본다)이고 5MB 이하다. "
					+ "촬영 위치 같은 메타데이터는 빼고 저장한다. 받은 imageId를 글이나 메시지의 imageIds에 담는다. "
					+ "로그인이 필요하다.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResult<ImageUploadResponse> upload(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Parameter(description = "올릴 사진. JPEG, PNG, WebP 가운데 하나이고 5MB 이하")
			@RequestParam(value = "file", required = false) MultipartFile file) {
		return ApiResult.ok(imageService.upload(userId, file));
	}

}
