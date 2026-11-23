package com.itting.user;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/users/{userId}/profile}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class UserProfileApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps steps;

	@PersistenceContext
	EntityManager entityManager;

	Members members;

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mockMvc, testUsers, steps, jdbcTemplate);
	}

	@Test
	void 다른_사람의_프로필은_사용자_표시와_관심사를_주고_겹치는_관심사만_isShared가_참이다() throws Exception {
		List<Long> interests = steps.interestIds(5);
		List<Long> questions = steps.questionIds(3);
		Member viewer = members.onboard("viewer@example.com", "보는두더지", interests.subList(0, 3), questions);
		Member target = members.onboard("target@example.com", "보이는두더지",
				List.of(interests.get(4), interests.get(2), interests.get(3)), questions);

		mockMvc.perform(get("/api/v1/users/{userId}/profile", target.userId()).cookie(viewer.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.user.id").value(target.userId()))
				.andExpect(jsonPath("$.data.user.nickname").value("보이는두더지"))
				.andExpect(jsonPath("$.data.user.profileImageUrl").isEmpty())
				.andExpect(jsonPath("$.data.user.mole.head").value("CAP_RED"))
				.andExpect(jsonPath("$.data.user.mole.neck").value("SCARF_GREEN"))
				.andExpect(jsonPath("$.data.user.campusName").value("서울"))
				.andExpect(jsonPath("$.data.user.generation").value(15))
				.andExpect(jsonPath("$.data.user.floor").value(2))
				.andExpect(jsonPath("$.data.user.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.interests[*].id").value(contains(
						interests.get(4).intValue(), interests.get(2).intValue(), interests.get(3).intValue())))
				.andExpect(jsonPath("$.data.interests[*].isShared").value(contains(false, true, false)))
				.andExpect(jsonPath("$.data.interests[0].name").isString())
				.andExpect(jsonPath("$.data.interests[0].interestCategoryId").value(1))
				.andExpect(jsonPath("$.data.intro").doesNotExist())
				.andExpect(jsonPath("$.data.answers").doesNotExist());
	}

	@Test
	void 자기_자신의_프로필은_isShared가_모두_거짓이다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");

		mockMvc.perform(get("/api/v1/users/{userId}/profile", mole.userId()).cookie(mole.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.interests[*].isShared").value(contains(false, false, false)));
	}

	@Test
	void 탈퇴한_사람은_404다() throws Exception {
		Member viewer = members.onboard("viewer@example.com", "보는두더지");
		Member target = members.onboard("target@example.com", "보이는두더지");
		entityManager.flush();
		testUsers.withdraw(target.userId());

		mockMvc.perform(get("/api/v1/users/{userId}/profile", target.userId()).cookie(viewer.session()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
	}

	@Test
	void 온보딩을_마치지_않은_사람과_없는_사람은_404다() throws Exception {
		Member viewer = members.onboard("viewer@example.com", "보는두더지");
		long joining = testUsers.createEmailUser("joining@example.com", Members.PASSWORD);

		mockMvc.perform(get("/api/v1/users/{userId}/profile", joining).cookie(viewer.session()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/users/{userId}/profile", 999_999).cookie(viewer.session()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/users/{userId}/profile", 1))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

}
