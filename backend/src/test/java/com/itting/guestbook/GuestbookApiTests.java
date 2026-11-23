package com.itting.guestbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/burrows/{burrowId}/guestbook-entries}, {@code DELETE /api/v1/guestbook-entries/{guestbookEntryId}}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GuestbookApiTests {

	private static final Instant T0 = Instant.parse("2026-11-17T03:05:48Z");

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

	Member owner;

	Member visitorA;

	Member visitorB;

	@BeforeEach
	void setUp() throws Exception {
		steps.clearNicknameHolds();
		members = new Members(mockMvc, testUsers, steps, jdbcTemplate);
		owner = members.onboard("owner@example.com", "땅굴주인");
		visitorA = members.onboard("a@example.com", "방문자가");
		visitorB = members.onboard("b@example.com", "방문자나");
		entityManager.flush();
	}

	// GET /api/v1/burrows/{burrowId}/guestbook-entries

	@Test
	void 주인이_보면_가려지지_않은_글을_최신순으로_주고_모두_지우고_신고할_수_있다() throws Exception {
		long first = members.writeGuestbook(owner.burrowId(), visitorA.userId(), "처음 쓴 글", T0);
		long second = members.writeGuestbook(owner.burrowId(), visitorB.userId(), "두 번째 글", T0.plusSeconds(60));
		long third = members.writeGuestbook(owner.burrowId(), visitorA.userId(), "같은 시각의 글", T0.plusSeconds(60));
		members.writeHiddenGuestbook(owner.burrowId(), visitorB.userId(), "가려진 글", T0.plusSeconds(120));

		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", owner.burrowId())
						.cookie(owner.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) third, (int) second, (int) first)))
				.andExpect(jsonPath("$.data.items[*].canDelete").value(contains(true, true, true)))
				.andExpect(jsonPath("$.data.items[*].canReport").value(contains(true, true, true)))
				.andExpect(jsonPath("$.data.items[0].content").value("같은 시각의 글"))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-17T03:06:48Z"))
				.andExpect(jsonPath("$.data.items[0].author.id").value(visitorA.userId()))
				.andExpect(jsonPath("$.data.items[0].author.nickname").value("방문자가"))
				.andExpect(jsonPath("$.data.items[0].author.mole.head").value("CAP_RED"))
				.andExpect(jsonPath("$.data.items[0].author.campusName").value("서울"))
				.andExpect(jsonPath("$.data.items[0].author.generation").value(15))
				.andExpect(jsonPath("$.data.items[0].author.floor").value(2))
				.andExpect(jsonPath("$.data.items[0].author.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(5))
				.andExpect(jsonPath("$.data.totalItems").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void 방문자가_보면_자기_글만_지울_수_있고_자기_글은_신고할_수_없다() throws Exception {
		long mine = members.writeGuestbook(owner.burrowId(), visitorA.userId(), "내가 쓴 글", T0);
		long others = members.writeGuestbook(owner.burrowId(), visitorB.userId(), "남이 쓴 글", T0.plusSeconds(60));

		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", owner.burrowId())
						.cookie(visitorA.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) others, (int) mine)))
				.andExpect(jsonPath("$.data.items[*].canDelete").value(contains(false, true)))
				.andExpect(jsonPath("$.data.items[*].canReport").value(contains(true, false)));
	}

	@Test
	void 탈퇴한_사람이_쓴_글은_작성자를_탈퇴한_사용자로_준다() throws Exception {
		members.writeGuestbook(owner.burrowId(), visitorB.userId(), "탈퇴 전에 쓴 글", T0);
		testUsers.withdraw(visitorB.userId());
		jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", visitorB.userId());

		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", owner.burrowId())
						.cookie(owner.session()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "data": {
						    "items": [
						      {
						        "author": {
						          "id": %d,
						          "nickname": null,
						          "profileImageUrl": null,
						          "mole": { "head": null, "face": null, "neck": null, "hand": null },
						          "campusName": null,
						          "generation": null,
						          "floor": null,
						          "isWithdrawn": true
						        },
						        "canDelete": true,
						        "canReport": true
						      }
						    ]
						  }
						}
						""".formatted(visitorB.userId()), JsonCompareMode.LENIENT));
	}

	@Test
	void 페이지를_나눠_주고_글이_있는_페이지를_넘으면_빈_목록이다() throws Exception {
		for (int i = 0; i < 7; i++) {
			members.writeGuestbook(owner.burrowId(), visitorA.userId(), "글 " + i, T0.plusSeconds(i));
		}

		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", owner.burrowId())
						.param("page", "2")
						.param("size", "5")
						.cookie(owner.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].content").value(contains("글 1", "글 0")))
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.totalItems").value(7))
				.andExpect(jsonPath("$.data.totalPages").value(2));
		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", owner.burrowId())
						.param("page", "3")
						.cookie(owner.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.totalItems").value(7))
				.andExpect(jsonPath("$.data.totalPages").value(2));
	}

	@Test
	void 글이_없으면_빈_목록과_0을_준다() throws Exception {
		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", owner.burrowId())
						.cookie(owner.session()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": { "items": [], "page": 1, "size": 5, "totalItems": 0, "totalPages": 0 },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 없는_땅굴이면_404다() throws Exception {
		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", 999_999)
						.cookie(owner.session()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("BURROW_NOT_FOUND"));
	}

	@Test
	void page나_size가_범위를_벗어나면_400과_fields를_준다() throws Exception {
		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", owner.burrowId())
						.param("page", "0")
						.param("size", "21")
						.cookie(owner.session()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(
						org.hamcrest.Matchers.containsInAnyOrder("page", "size")));
	}

	@Test
	void 방명록_목록은_로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/burrows/{burrowId}/guestbook-entries", owner.burrowId()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	// DELETE /api/v1/guestbook-entries/{guestbookEntryId}

	@Test
	void 땅굴_주인은_남이_쓴_글을_지운다() throws Exception {
		long entry = members.writeGuestbook(owner.burrowId(), visitorA.userId(), "지울 글", T0);

		mockMvc.perform(delete("/api/v1/guestbook-entries/{id}", entry).cookie(owner.session()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));
		entityManager.flush();

		assertThat(countEntry(entry)).isZero();
		mockMvc.perform(get("/api/v1/me/burrow").cookie(owner.session()))
				.andExpect(jsonPath("$.data.guestbookCount").value(0));
	}

	@Test
	void 쓴_사람은_자기_글을_지운다() throws Exception {
		long entry = members.writeGuestbook(owner.burrowId(), visitorA.userId(), "지울 글", T0);

		mockMvc.perform(delete("/api/v1/guestbook-entries/{id}", entry).cookie(visitorA.session()))
				.andExpect(status().isOk());
		entityManager.flush();

		assertThat(countEntry(entry)).isZero();
	}

	@Test
	void 주인도_쓴_사람도_아니면_403이고_글은_남는다() throws Exception {
		long entry = members.writeGuestbook(owner.burrowId(), visitorA.userId(), "남의 글", T0);

		mockMvc.perform(delete("/api/v1/guestbook-entries/{id}", entry).cookie(visitorB.session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		entityManager.flush();

		assertThat(countEntry(entry)).isEqualTo(1);
	}

	@Test
	void 없는_글이나_가려진_글은_404다() throws Exception {
		long hidden = members.writeHiddenGuestbook(owner.burrowId(), visitorA.userId(), "가려진 글", T0);

		mockMvc.perform(delete("/api/v1/guestbook-entries/{id}", 999_999).cookie(owner.session()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("GUESTBOOK_ENTRY_NOT_FOUND"));
		mockMvc.perform(delete("/api/v1/guestbook-entries/{id}", hidden).cookie(owner.session()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("GUESTBOOK_ENTRY_NOT_FOUND"));
		entityManager.flush();

		assertThat(countEntry(hidden)).isEqualTo(1);
	}

	@Test
	void 방명록_삭제는_로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(delete("/api/v1/guestbook-entries/{id}", 1))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private int countEntry(long entryId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM guestbook_entry WHERE id = ?", Integer.class,
				entryId);
	}

}
