package com.itting.term.repository;

import java.time.Instant;
import java.util.List;

import com.itting.term.domain.Term;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

	/** 이미 열린 학기를 최근에 열린 것부터. 연 시각이 같으면 id가 큰 것부터다. */
	List<Term> findAllByOpenedAtLessThanEqualOrderByOpenedAtDescIdDesc(Instant now);

}
