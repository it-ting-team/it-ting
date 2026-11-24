package com.itting.interest.repository;

/**
 * 한 사람이 고른 관심사 가운데 다른 사람과 겹치는 것 하나.
 *
 * @param ownerId 관심사를 고른 사람
 */
public record UserSharedInterest(Long ownerId, Long interestId, String name, Long interestCategoryId) {
}
