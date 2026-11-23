package com.itting.community.domain;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 올린 사진({@code uploaded_image}). 글이나 채팅 메시지에 붙이기 전에 먼저 올린다.
 * 올린 사람만 자기 사진을 붙일 수 있다. 올리는 API는 08 커뮤니티 ③에서 만든다.
 */
@Entity
@Table(name = "uploaded_image")
public class UploadedImage extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "uploader_id", nullable = false, updatable = false)
	private Long uploaderId;

	@Column(name = "image_url", nullable = false, updatable = false, length = 500)
	private String imageUrl;

	protected UploadedImage() {
	}

	public static UploadedImage create(Long uploaderId, String imageUrl) {
		UploadedImage image = new UploadedImage();
		image.uploaderId = uploaderId;
		image.imageUrl = imageUrl;
		return image;
	}

	public Long getId() {
		return id;
	}

	public Long getUploaderId() {
		return uploaderId;
	}

	public String getImageUrl() {
		return imageUrl;
	}

}
