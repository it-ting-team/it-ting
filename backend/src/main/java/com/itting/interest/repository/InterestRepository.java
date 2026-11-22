package com.itting.interest.repository;

import java.util.List;

import com.itting.interest.domain.Interest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestRepository extends JpaRepository<Interest, Long> {

	/** 지우지 않은 세부 관심사를 id 순으로. 순서 칼럼이 없어 id 순이다. */
	List<Interest> findAllByDeletedAtIsNullOrderByIdAsc();

}
