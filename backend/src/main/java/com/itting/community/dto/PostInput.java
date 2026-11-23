package com.itting.community.dto;

import java.util.List;

import com.itting.community.domain.PostCategory;

/**
 * 글쓰기와 글 수정이 함께 받는 값. 서비스가 두 요청을 같은 규칙으로 검사하고 저장한다.
 */
public interface PostInput {

	PostCategory category();

	String title();

	String content();

	List<Long> interestCategoryIds();

	List<Long> imageIds();

	/** 연결할 모임의 id. 연결하지 않으면 null. */
	default Long linkedMeetupId() {
		return null;
	}

}
