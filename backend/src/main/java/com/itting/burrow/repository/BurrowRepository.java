package com.itting.burrow.repository;

import java.util.Optional;

import com.itting.burrow.domain.Burrow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BurrowRepository extends JpaRepository<Burrow, Long> {

	Optional<Burrow> findByUserId(Long userId);

}
