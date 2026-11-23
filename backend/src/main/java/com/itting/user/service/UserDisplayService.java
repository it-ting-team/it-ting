package com.itting.user.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itting.user.dto.UserDisplayResponse;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "사용자 표시"({@link UserDisplayResponse})를 만든다. 목록에 나오는 여러 사람을 쿼리 한 번으로 읽는다.
 * 캠퍼스와 층은 프로필에 저장된 지금의 값이다.
 */
@Service
public class UserDisplayService {

	private final NamedParameterJdbcTemplate jdbc;

	public UserDisplayService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public UserDisplayResponse get(Long userId) {
		return getAll(Set.of(userId)).get(userId);
	}

	/**
	 * @return 계정 id → 사용자 표시. 없는 계정 id는 담지 않는다
	 */
	@Transactional(readOnly = true)
	public Map<Long, UserDisplayResponse> getAll(Collection<Long> userIds) {
		Map<Long, UserDisplayResponse> result = new HashMap<>();
		if (userIds.isEmpty()) {
			return result;
		}
		List<UserDisplayResponse> rows = jdbc.query("""
				SELECT u.id, u.status, p.nickname, p.profile_image_url, p.mole_head, p.mole_face, p.mole_neck,
				       p.mole_hand, p.generation, c.name AS campus_name, f.floor
				FROM `user` u
				LEFT JOIN user_profile p ON p.user_id = u.id
				LEFT JOIN campus_floor f ON f.id = p.campus_floor_id
				LEFT JOIN campus c ON c.id = f.campus_id
				WHERE u.id IN (:ids)
				""", Map.of("ids", Set.copyOf(userIds)), (rs, rowNum) -> {
			long id = rs.getLong("id");
			if ("WITHDRAWN".equals(rs.getString("status")) || rs.getObject("generation") == null) {
				return UserDisplayResponse.withdrawn(id);
			}
			return new UserDisplayResponse(id, rs.getString("nickname"), rs.getString("profile_image_url"),
					new UserDisplayResponse.Mole(rs.getString("mole_head"), rs.getString("mole_face"),
							rs.getString("mole_neck"), rs.getString("mole_hand")),
					rs.getString("campus_name"), rs.getInt("generation"), rs.getInt("floor"), false);
		});
		rows.forEach(row -> result.put(row.id(), row));
		return result;
	}

}
