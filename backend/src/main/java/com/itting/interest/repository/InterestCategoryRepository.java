package com.itting.interest.repository;

import java.util.List;

import com.itting.interest.domain.InterestCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestCategoryRepository extends JpaRepository<InterestCategory, Long> {

	/** 지우지 않은 대분류를 id 순으로. 순서 칼럼이 없어 id 순이다. */
	List<InterestCategory> findAllByDeletedAtIsNullOrderByIdAsc();

}
