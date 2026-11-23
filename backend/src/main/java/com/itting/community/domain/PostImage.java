package com.itting.community.domain;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 글에 붙인 사진과 보여 주는 순서({@code post_image}). {@code sort_order} 1이 대표 사진이다.
 * 사진 하나는 글 하나에만 붙는다.
 */
@Entity
@Table(name = "post_image")
public class PostImage extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false, updatable = false)
	private Long postId;

	@Column(name = "sort_order", nullable = false, updatable = false)
	private byte sortOrder;

	@Column(name = "image_id", nullable = false, updatable = false)
	private Long imageId;

	protected PostImage() {
	}

	/**
	 * @param sortOrder 1 ~ 3. 1이 대표 사진이다
	 */
	public static PostImage create(Long postId, int sortOrder, Long imageId) {
		PostImage postImage = new PostImage();
		postImage.postId = postId;
		postImage.sortOrder = (byte) sortOrder;
		postImage.imageId = imageId;
		return postImage;
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public Long getImageId() {
		return imageId;
	}

}
