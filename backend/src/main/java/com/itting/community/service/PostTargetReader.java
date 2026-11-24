package com.itting.community.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.itting.community.repository.PostQueryRepository;
import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.service.NotificationTargetReader;
import com.itting.notification.service.NotificationTargetSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 알림({@link NotificationTargetType#POST})의 갈 곳 이름으로 글 제목을 준다. 지금 보이는 버전(번호가 가장 큰 것)의
 * 제목이고, 삭제한 글은 결과에 담지 않는다. 신고로 가려진 글은 제목을 준다. 가려진 글의 알림을 받는 사람은 글 작성자와 그 글에
 * 댓글을 단 사람이라, 제목이 없으면 어느 글인지 알 수 없다(KAN-52).
 */
@Component
public class PostTargetReader implements NotificationTargetReader {

	private final PostQueryRepository postQueryRepository;

	public PostTargetReader(PostQueryRepository postQueryRepository) {
		this.postQueryRepository = postQueryRepository;
	}

	@Override
	public NotificationTargetType targetType() {
		return NotificationTargetType.POST;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, NotificationTargetSummary> read(Collection<Long> targetIds) {
		return postQueryRepository.findTitles(targetIds).entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> new NotificationTargetSummary(entry.getValue(), null)));
	}

}
