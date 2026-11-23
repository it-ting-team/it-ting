package com.itting.community.domain;

/** 글 목록의 정렬. 값이 같으면 새로 쓴 글이 먼저다. */
public enum PostSort {
	/** 쓴 시각이 늦은 것부터. */
	LATEST,
	/** 인기 점수(최근 7일에 받은 좋아요 수)가 높은 것부터. */
	POPULAR,
	/** 댓글 수(삭제되지 않은 댓글과 답글)가 많은 것부터. */
	MOST_COMMENTED
}
