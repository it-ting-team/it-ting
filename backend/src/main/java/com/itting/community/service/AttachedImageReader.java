package com.itting.community.service;

import java.util.Collection;
import java.util.Set;

/**
 * 글이 아닌 곳에 붙은 사진을 알려 준다. 올린 사진({@code uploaded_image})을 자기 테이블로 붙이는 영역이 구현한다
 * (채팅 메시지의 {@code chat_message_image}). 글에 붙은 사진({@code post_image})은 커뮤니티가 직접 본다.
 *
 * <p>사진 하나는 글 하나나 메시지 하나에만 붙는다. {@link ImageUsageService}가 붙이기 전에 모든 구현에 물어, 이미 붙은 사진을
 * 다시 붙이지 못하게 한다.
 */
public interface AttachedImageReader {

	/**
	 * @return 이 사진들 가운데 이 영역에 이미 붙어 있는 것의 id. 없으면 빈 집합
	 */
	Set<Long> findAttached(Collection<Long> imageIds);

}
