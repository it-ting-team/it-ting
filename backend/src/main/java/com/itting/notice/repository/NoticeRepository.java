package com.itting.notice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.itting.notice.domain.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

	/** 삭제하지 않은 공지를 한 페이지. 순서는 {@code pageable}의 정렬을 따른다. */
	Page<Notice> findByDeletedAtIsNull(Pageable pageable);

	/** 삭제하지 않은 공지 하나. */
	Optional<Notice> findByIdAndDeletedAtIsNull(Long id);

	/** id 가운데 삭제하지 않은 공지. 공지 알림의 제목을 읽을 때 쓴다. */
	List<Notice> findByIdInAndDeletedAtIsNull(Collection<Long> ids);

}
