package com.itting.interest.repository;

/** 여러 계정의 관심사 태그를 읽을 때 주인과 함께 가져오는 값. */
public record UserInterestTagRow(Long ownerId, Long id, String name, Long interestCategoryId) {
}
