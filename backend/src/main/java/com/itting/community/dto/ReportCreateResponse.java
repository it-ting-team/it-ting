package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/reports}의 {@code data}.
 */
public record ReportCreateResponse(@Schema(description = "신고의 id", example = "61") Long reportId) {
}
