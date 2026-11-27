package com.itting.chat.service;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import com.itting.chat.domain.ChatMessageImage;
import com.itting.chat.repository.ChatMessageImageRepository;
import com.itting.community.service.AttachedImageReader;
import org.springframework.stereotype.Component;

/**
 * 채팅 메시지에 붙은 사진({@code chat_message_image})을 커뮤니티의 사진 검사에 알려 준다. 메시지에 붙은 사진은 글이나 다른
 * 메시지에 다시 붙일 수 없다.
 */
@Component
class ChatAttachedImageReader implements AttachedImageReader {

	private final ChatMessageImageRepository imageRepository;

	ChatAttachedImageReader(ChatMessageImageRepository imageRepository) {
		this.imageRepository = imageRepository;
	}

	@Override
	public Set<Long> findAttached(Collection<Long> imageIds) {
		if (imageIds.isEmpty()) {
			return Set.of();
		}
		return imageRepository.findAllByImageIdIn(imageIds).stream()
				.map(ChatMessageImage::getImageId)
				.collect(Collectors.toSet());
	}

}
