package com.itting.community.repository;

import com.itting.community.domain.Report;
import com.itting.community.domain.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

	/**
	 * 대상의 마지막 복구 뒤에 접수된 신고의 수. 한 사람은 한 대상을 한 번만 신고하므로 서로 다른 신고자의 수와 같다.
	 * 복구한 적이 없으면 그 대상의 신고를 모두 센다({@code docs/db/community.md}의 재가림 판단).
	 *
	 * @param targetType {@code POST}, {@code COMMENT}, {@code GUESTBOOK}
	 */
	@Query(value = """
			SELECT COUNT(*) FROM report r
			WHERE r.target_type = :targetType AND r.target_id = :targetId
			  AND r.id > COALESCE((SELECT ra.restored_through_report_id FROM report_action ra
			                       WHERE ra.target_type = :targetType AND ra.target_id = :targetId
			                         AND ra.action_type = 'TARGET_RESTORED'
			                       ORDER BY ra.id DESC LIMIT 1), 0)
			""", nativeQuery = true)
	long countSinceLastRestore(@Param("targetType") String targetType, @Param("targetId") Long targetId);

}
