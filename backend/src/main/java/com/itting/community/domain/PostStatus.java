package com.itting.community.domain;

/**
 * 글과 댓글의 상태({@code post.status}, {@code comment.status}).
 * 서로 다른 3명이 신고하면 {@link #HIDDEN}이 되고, 지우면 {@link #DELETED}가 된다.
 */
public enum PostStatus {
	PUBLIC,
	HIDDEN,
	DELETED
}
