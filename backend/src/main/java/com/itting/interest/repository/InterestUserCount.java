package com.itting.interest.repository;

/**
 * 관심사 하나를 고른 사람 수.
 */
public record InterestUserCount(Long interestId, long userCount) {
}
