package com.itting.term.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.itting.global.error.BusinessException;
import com.itting.term.domain.Term;
import com.itting.term.domain.TermErrorCode;
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

	/**
	 * 이번 학기의 id. 이미 열린 학기 가운데 가장 최근에 열린 것이고, 학기 목록의 첫 번째와 같다.
	 *
	 * @return 열린 학기가 하나도 없으면 빈 값
	 */
	public Optional<Long> findCurrentTermId() {
		return findCurrentTermId(clock.instant());
	}

	/** 이미 열린 학기만 조회한다. 요청한 학기와 이번 학기를 같은 시각으로 판단한다. */
	public TermResponse getOpenedTerm(Long termId) {
		Instant now = clock.instant();
		Term term = termRepository.findById(termId)
				.filter(candidate -> !candidate.getOpenedAt().isAfter(now))
				.orElseThrow(() -> new BusinessException(TermErrorCode.TERM_NOT_FOUND));
		boolean current = findCurrentTermId(now).filter(termId::equals).isPresent();
		return new TermResponse(termId, term.getYear(), term.getHalf(), current);
	}

	private Optional<Long> findCurrentTermId(Instant now) {
		return termRepository.findFirstByOpenedAtLessThanEqualOrderByOpenedAtDescIdDesc(now).map(Term::getId);
	}

}
