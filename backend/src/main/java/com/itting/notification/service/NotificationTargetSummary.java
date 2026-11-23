package com.itting.notification.service;

import java.time.Instant;

/**
 * 알림 한 줄의 갈 곳에 붙이는 값.
 *
 * @param title 갈 곳의 이름. 모임 이름, 글 제목, 공지 제목, 모임 대화방이면 모임 이름. 없으면 null
 * @param startsAt 갈 곳이 모임일 때 그 모임의 시작 시각. 그 밖이면 null
 */
public record NotificationTargetSummary(String title, Instant startsAt) {
}
