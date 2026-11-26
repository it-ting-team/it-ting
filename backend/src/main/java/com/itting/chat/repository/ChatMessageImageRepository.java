package com.itting.chat.repository;

import java.util.Collection;
import java.util.List;

import com.itting.chat.domain.ChatMessageImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageImageRepository extends JpaRepository<ChatMessageImage, ChatMessageImage.Key> {

	/** 이 메시지들에 붙은 사진. 순서는 정해 주지 않는다. */
	List<ChatMessageImage> findAllByChatMessageIdIn(Collection<Long> chatMessageIds);

	/** 이 사진들 가운데 메시지에 붙은 것. */
	List<ChatMessageImage> findAllByImageIdIn(Collection<Long> imageIds);

}
