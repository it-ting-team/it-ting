package com.itting.term.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import com.itting.term.domain.Term;
import com.itting.term.dto.TermListResponse;
import com.itting.term.dto.TermListResponse.TermResponse;
import com.itting.term.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TermService {

	private final TermRepository termRepository;

	private final Clock clock;

	public TermService(TermRepository termRepository, Clock clock) {
		this.termRepository = termRepository;
		this.clock = clock;
	}

	/**
	 * 이미 열린 학기를 최근에 열린 것부터 준다. 첫 번째가 이번 학기다. 아직 열리지 않은 학기는 뺀다.
	 */
	public TermListResponse getTerms() {
		List<Term> terms = termRepository.findAllByOpenedAtLessThanEqualOrderByOpenedAtDescIdDesc(clock.instant());
		List<TermResponse> items = new ArrayList<>(terms.size());
		for (int i = 0; i < terms.size(); i++) {
			Term term = terms.get(i);
			items.add(new TermResponse(term.getId(), term.getYear(), term.getHalf(), i == 0));
		}
		return new TermListResponse(items);
	}

}
