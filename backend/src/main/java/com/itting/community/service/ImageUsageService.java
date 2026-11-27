package com.itting.community.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.itting.community.domain.UploadedImage;
import com.itting.community.repository.PostImageRepository;
import com.itting.community.repository.UploadedImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 올린 사진({@code uploaded_image})을 붙일 수 있는지와 사진의 주소. 글쓰기와 채팅 메시지 보내기가 함께 쓴다
 * ({@code docs/api/08-community.md}의 "사진": 다른 사람이 올린 사진이나, 이미 글이나 채팅 메시지에 붙은 사진은 쓸 수 없다).
 * 데이터를 바꾸지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ImageUsageService {

	private final UploadedImageRepository uploadedImageRepository;

	private final PostImageRepository postImageRepository;

	private final List<AttachedImageReader> otherAttachments;

	public ImageUsageService(UploadedImageRepository uploadedImageRepository,
			PostImageRepository postImageRepository, List<AttachedImageReader> otherAttachments) {
		this.uploadedImageRepository = uploadedImageRepository;
		this.postImageRepository = postImageRepository;
		this.otherAttachments = otherAttachments;
	}

	/**
	 * 이 사람이 이 사진들을 붙일 수 있는지. 모두 있는 사진이고, 이 사람이 올렸고, 다른 글이나 채팅 메시지에 붙지 않았어야 한다.
	 *
	 * @param postId 고치는 글. 이미 이 글에 붙은 사진은 다시 붙여도 된다. 새 글이거나 글이 아닌 곳에 붙이면 null
	 */
	public boolean usable(Long userId, Long postId, Collection<Long> imageIds) {
		if (imageIds.isEmpty()) {
			return true;
		}
		Set<Long> ids = new HashSet<>(imageIds);
		Map<Long, UploadedImage> images = uploadedImageRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(UploadedImage::getId, Function.identity()));
		if (images.size() != ids.size()
				|| images.values().stream().anyMatch(image -> !image.getUploaderId().equals(userId))) {
			return false;
		}
		boolean onOtherPost = postImageRepository.findAllByImageIdIn(ids).stream()
				.anyMatch(attached -> !Objects.equals(attached.getPostId(), postId));
		if (onOtherPost) {
			return false;
		}
		return otherAttachments.stream().allMatch(reader -> reader.findAttached(ids).isEmpty());
	}

	/**
	 * @return 사진 id → 사진의 주소. 없는 사진은 담지 않는다
	 */
	public Map<Long, String> urlsOf(Collection<Long> imageIds) {
		Map<Long, String> urls = new HashMap<>();
		if (imageIds.isEmpty()) {
			return urls;
		}
		uploadedImageRepository.findAllById(new HashSet<>(imageIds))
				.forEach(image -> urls.put(image.getId(), image.getImageUrl()));
		return urls;
	}

}
