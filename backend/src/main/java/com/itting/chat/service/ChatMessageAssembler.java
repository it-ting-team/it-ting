package com.itting.chat.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.itting.chat.domain.ChatMessage;
import com.itting.chat.domain.ChatMessageImage;
import com.itting.chat.domain.ChatMessageType;
import com.itting.chat.dto.ChatMessageResponse;
import com.itting.chat.dto.ChatMessageResponse.Image;
import com.itting.chat.dto.ChatMessageResponse.MeetupCard;
import com.itting.chat.repository.ChatMessageImageRepository;
import com.itting.community.service.ImageUsageService;
import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;
import com.itting.meetup.service.MeetupChatInfoReader;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Component;

/**
 * 저장된 메시지를 응답의 "메시지" 모양으로 만든다({@code docs/api/06-chat.md}의 "메시지"). 메시지 목록, 대화 목록의 마지막
 * 메시지, 보낸 뒤의 응답이 함께 쓴다. 보낸 사람 · 사진 · 모임 카드는 메시지마다 따로 읽지 않고 한 번에 읽는다.
 *
 * <p>모임 카드는 저장하지 않는다. 사람이 보낸 메시지의 본문에서 첫 모임 링크를 찾아 그 모임의 지금 값을 담는다. 없는 모임이나
 * 삭제된 모임이면 null이다.
 */
@Component
class ChatMessageAssembler {

	private final ChatMessageImageRepository imageRepository;

	private final UserDisplayService userDisplayService;

	private final ImageUsageService imageUsageService;

	private final MeetupChatInfoReader meetups;

	private final MeetupLinkParser linkParser;

	ChatMessageAssembler(ChatMessageImageRepository imageRepository, UserDisplayService userDisplayService,
			ImageUsageService imageUsageService, MeetupChatInfoReader meetups, MeetupLinkParser linkParser) {
		this.imageRepository = imageRepository;
		this.userDisplayService = userDisplayService;
		this.imageUsageService = imageUsageService;
		this.meetups = meetups;
		this.linkParser = linkParser;
	}

	ChatMessageResponse assemble(ChatMessage message) {
		return assemble(List.of(message)).getFirst();
	}

	/**
	 * @return 받은 순서 그대로의 메시지
	 */
	List<ChatMessageResponse> assemble(List<ChatMessage> messages) {
		if (messages.isEmpty()) {
			return List.of();
		}
		Map<Long, UserDisplayResponse> senders = userDisplayService.getAll(messages.stream()
				.map(ChatMessage::getSenderId)
				.collect(Collectors.toSet()));
		Map<Long, List<Image>> images = images(messages.stream().map(ChatMessage::getId).toList());
		Map<Long, Long> linkedMeetupIds = linkedMeetupIds(messages);
		Map<Long, MeetupCardResponse> cards = meetups.findCards(new HashSet<>(linkedMeetupIds.values()));
		return messages.stream()
				.map(message -> {
					Long meetupId = linkedMeetupIds.get(message.getId());
					return new ChatMessageResponse(message.getId(), message.getType(),
							senders.get(message.getSenderId()), message.getContent(),
							meetupId == null ? null : card(cards.get(meetupId)),
							images.getOrDefault(message.getId(), List.of()), message.getCreatedAt());
				})
				.toList();
	}

	/** 메시지 id → 붙인 사진. 보낸 순서대로다. 사진이 없는 메시지는 담지 않는다. */
	private Map<Long, List<Image>> images(Collection<Long> messageIds) {
		List<ChatMessageImage> attached = imageRepository.findAllByChatMessageIdIn(messageIds);
		if (attached.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> urls = imageUsageService.urlsOf(attached.stream().map(ChatMessageImage::getImageId).toList());
		return attached.stream()
				.sorted(Comparator.comparingInt(ChatMessageImage::getSortOrder))
				.collect(Collectors.groupingBy(ChatMessageImage::getChatMessageId,
						Collectors.mapping(image -> new Image(image.getImageId(), urls.get(image.getImageId())),
								Collectors.toList())));
	}

	/** 메시지 id → 본문의 첫 모임 링크가 가리키는 모임 id. 사람이 보낸 메시지만 본다. */
	private Map<Long, Long> linkedMeetupIds(List<ChatMessage> messages) {
		Map<Long, Long> linked = new HashMap<>();
		for (ChatMessage message : messages) {
			if (message.getType() != ChatMessageType.TEXT) {
				continue;
			}
			Long meetupId = linkParser.firstMeetupId(message.getContent());
			if (meetupId != null) {
				linked.put(message.getId(), meetupId);
			}
		}
		return linked;
	}

	private static MeetupCard card(MeetupCardResponse meetup) {
		if (meetup == null) {
			return null;
		}
		return new MeetupCard(meetup.id(), meetup.title(), meetup.startsAt(), meetup.capacity(),
				meetup.confirmedCount());
	}

}
