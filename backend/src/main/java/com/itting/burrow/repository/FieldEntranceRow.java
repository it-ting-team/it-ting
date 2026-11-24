package com.itting.burrow.repository;

/** 들판 입구와 방노트에 필요한 주인의 기본 정보. */
public record FieldEntranceRow(Long burrowId, Long ownerId, int slot, String intro) {
}
