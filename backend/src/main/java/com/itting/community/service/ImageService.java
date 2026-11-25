package com.itting.community.service;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.itting.community.domain.ImageRules;
import com.itting.community.domain.UploadedImage;
import com.itting.community.dto.ImageUploadResponse;
import com.itting.community.repository.UploadedImageRepository;
import com.itting.community.service.image.ImageFormat;
import com.itting.community.service.image.ImageMetadataStripper;
import com.itting.community.service.image.ImageStorage;
import com.itting.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사진 올리기({@code POST /api/v1/images}). 검사하고, 메타데이터를 걷어내고, 저장소에 둔 뒤 {@code uploaded_image}에 한 줄을
 * 만든다. 올리기만 한 사진은 어디에도 붙지 않는다. 글이나 메시지가 {@code imageIds}로 붙인다.
 *
 * <p>파일을 쓰는 동안 DB 연결을 잡지 않도록 트랜잭션은 줄을 만들 때만 쓴다.
 */
@Service
public class ImageService {

	private final ImageStorage imageStorage;

	private final UploadedImageRepository uploadedImageRepository;

	public ImageService(ImageStorage imageStorage, UploadedImageRepository uploadedImageRepository) {
		this.imageStorage = imageStorage;
		this.uploadedImageRepository = uploadedImageRepository;
	}

	/**
	 * @throws BusinessException 파일이 없거나 비어 있음, JPEG · PNG · WebP가 아님(내용으로 본다), 5MB를 넘음이면
	 *         {@code INVALID_INPUT}과 {@code file}
	 */
	public ImageUploadResponse upload(Long userId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw BusinessException.invalidInput("file", ImageRules.MISSING_MESSAGE);
		}
		if (file.getSize() > ImageRules.MAX_BYTES) {
			throw BusinessException.invalidInput("file", ImageRules.SIZE_MESSAGE);
		}
		byte[] bytes = read(file);
		ImageFormat format = ImageFormat.detect(bytes)
				.orElseThrow(() -> BusinessException.invalidInput("file", ImageRules.FORMAT_MESSAGE));
		byte[] cleaned;
		try {
			cleaned = ImageMetadataStripper.strip(bytes, format);
		}
		catch (IllegalArgumentException e) {
			throw BusinessException.invalidInput("file", ImageRules.FORMAT_MESSAGE);
		}
		String url = imageStorage.store(cleaned, format);
		UploadedImage image = uploadedImageRepository.save(UploadedImage.create(userId, url));
		return new ImageUploadResponse(image.getId(), image.getImageUrl());
	}

	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException("올린 파일을 읽지 못했습니다.", e);
		}
	}

}
