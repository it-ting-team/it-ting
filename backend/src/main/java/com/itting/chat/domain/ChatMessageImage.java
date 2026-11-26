package com.itting.chat.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 메시지에 붙인 사진과 보여 주는 순서({@code chat_message_image}). 메시지 하나에 3장까지이고, 사진 하나는 메시지 하나에만 붙는다.
 * 메시지를 고치거나 지우지 않으므로 이 줄도 바뀌지 않는다.
 */
@Entity
@Table(name = "chat_message_image")
@IdClass(ChatMessageImage.Key.class)
public class ChatMessageImage {

	@Id
	@Column(name = "chat_message_id", updatable = false)
	private Long chatMessageId;

	@Id
	@Column(name = "sort_order", updatable = false)
	private byte sortOrder;

	/** 붙인 사진({@code uploaded_image.id}). */
	@Column(name = "image_id", nullable = false, updatable = false)
	private Long imageId;

	protected ChatMessageImage() {
	}

	/**
	 * @param sortOrder 1 ~ 3. 보낸 순서다
	 */
	public static ChatMessageImage create(Long chatMessageId, int sortOrder, Long imageId) {
		ChatMessageImage image = new ChatMessageImage();
		image.chatMessageId = chatMessageId;
		image.sortOrder = (byte) sortOrder;
		image.imageId = imageId;
		return image;
	}

	public Long getChatMessageId() {
		return chatMessageId;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public Long getImageId() {
		return imageId;
	}

	/** 복합 기본키 {@code (chat_message_id, sort_order)}. */
	public record Key(Long chatMessageId, byte sortOrder) implements Serializable {
	}

}
