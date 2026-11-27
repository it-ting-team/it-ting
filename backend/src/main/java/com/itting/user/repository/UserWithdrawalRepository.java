package com.itting.user.repository;

import java.util.Optional;

import com.itting.user.domain.UserWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWithdrawalRepository extends JpaRepository<UserWithdrawal, Long> {

	Optional<UserWithdrawal> findByUserId(Long userId);

}
