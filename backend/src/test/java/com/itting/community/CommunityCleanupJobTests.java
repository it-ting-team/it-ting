package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.itting.community.service.CommunityCleanupJob;
import com.itting.community.service.image.ImageStorage;
import com.itting.global.config.ImageProperties;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 정리 작업(KAN-66): 삭제한 지 2주가 지난 글 · 댓글의 버전, 올린 지 24시간이 지나도 붙지 않은 사진.
 * 부르는 API가 없어 작업을 직접 부른다. 사진 파일은 테스트용 임시 폴더({@link TestcontainersConfiguration#IMAGE_DIR})에
 * 둔다.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class CommunityCleanupJobTests {

	private static final String LOCK_KEY = "itting:job-lock:community-cleanup";

	private static final Duration TWO_WEEKS = Duration.ofDays(14);

	private static final Duration ONE_DAY = Duration.ofHours(24);

	private static final Duration MINUTE = Duration.ofMinutes(1);

	@Autowired
	CommunityCleanupJob job;

	@Autowired
	ImageStorage imageStorage;

	@Autowired
	ImageProperties imageProperties;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chatSteps;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	MutableClock clock;

	long author;

	long other;

	Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		redis.delete(LOCK_KEY);
		author = steps.createMember("author@example.com", "글쓴두더지");
		other = steps.createMember("other@example.com", "다른두더지");
	}

	@AfterEach
	void tearDown() {
		redis.delete(LOCK_KEY);
	}

	@Test
	void 삭제한_지_2주가_지난_글은_버전과_사진과_파일이_지워지고_글_줄은_DELETED로_남는다() {
		long post = deletedPost(now.minus(TWO_WEEKS).minus(MINUTE));
		steps.addVersion(post, 2, "고친 제목", "고친 본문", now.minus(Duration.ofDays(20)));
		Image first = storedImage(now.minus(Duration.ofDays(20)));
		Image second = storedImage(now.minus(Duration.ofDays(20)));
		steps.attachImage(post, 1, first.id());
		steps.attachImage(post, 2, second.id());

		assertThat(job.runOnce()).isTrue();

		assertThat(count("SELECT COUNT(*) FROM post_version WHERE post_id = ?", post)).isZero();
		assertThat(count("SELECT COUNT(*) FROM post_image WHERE post_id = ?", post)).isZero();
		assertThat(imageExists(first.id())).isFalse();
		assertThat(imageExists(second.id())).isFalse();
		assertThat(Files.exists(first.file())).isFalse();
		assertThat(Files.exists(second.file())).isFalse();
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM post WHERE id = ?", String.class, post))
				.isEqualTo("DELETED");
	}

	@Test
	void 삭제한_지_2주가_안_된_글과_가려진_글과_공개_글은_그대로다() {
		long recent = deletedPost(now.minus(TWO_WEEKS).plus(MINUTE));
		Image recentImage = storedImage(now.minus(Duration.ofDays(20)));
		steps.attachImage(recent, 1, recentImage.id());
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가린 글", "본문", now.minus(Duration.ofDays(30)));
		long open = steps.insertPost(author, "FREE", "공개 글", "본문", now.minus(Duration.ofDays(30)));
		Image openImage = storedImage(now.minus(Duration.ofDays(30)));
		steps.attachImage(open, 1, openImage.id());

		job.runOnce();

		assertThat(count("SELECT COUNT(*) FROM post_version WHERE post_id IN (?, ?, ?)", recent, hidden, open))
				.isEqualTo(3);
		assertThat(imageExists(recentImage.id())).isTrue();
		assertThat(imageExists(openImage.id())).isTrue();
		assertThat(Files.exists(recentImage.file())).isTrue();
		assertThat(Files.exists(openImage.file())).isTrue();
	}

	@Test
	void 삭제한_지_2주가_지난_댓글과_답글은_버전만_지워지고_2주_안의_댓글은_남는다() {
		long post = steps.insertPost(author, "FREE", "글", "본문", now.minus(Duration.ofDays(30)));
		long oldComment = deletedComment(post, null, now.minus(TWO_WEEKS).minus(MINUTE));
		steps.addCommentVersion(oldComment, 2, "고친 댓글");
		long oldReply = deletedComment(post, oldComment, now.minus(TWO_WEEKS).minus(MINUTE));
		long recentComment = deletedComment(post, null, now.minus(TWO_WEEKS).plus(MINUTE));
		long publicComment = steps.comment(post, other, null, "PUBLIC");

		job.runOnce();

		assertThat(count("SELECT COUNT(*) FROM comment_version WHERE comment_id IN (?, ?)", oldComment, oldReply))
				.isZero();
		assertThat(count("SELECT COUNT(*) FROM comment_version WHERE comment_id = ?", recentComment)).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM comment_version WHERE comment_id = ?", publicComment)).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM comment WHERE id IN (?, ?) AND status = 'DELETED'", oldComment,
				oldReply)).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM post_version WHERE post_id = ?", post)).isEqualTo(1);
	}

	@Test
	void 올린_지_24시간이_지나도_붙지_않은_사진은_줄과_파일이_지워지고_24시간_안의_사진은_남는다() {
		Image old = storedImage(now.minus(ONE_DAY).minus(MINUTE));
		Image recent = storedImage(now.minus(ONE_DAY).plus(MINUTE));

		job.runOnce();

		assertThat(imageExists(old.id())).isFalse();
		assertThat(Files.exists(old.file())).isFalse();
		assertThat(imageExists(recent.id())).isTrue();
		assertThat(Files.exists(recent.file())).isTrue();
	}

	@Test
	void 글을_고쳐_뗀_사진은_지워지고_글과_채팅_메시지에_붙은_사진은_오래돼도_남는다() {
		long post = steps.insertPost(author, "FREE", "글", "본문", now.minus(Duration.ofDays(30)));
		Image detached = storedImage(now.minus(Duration.ofDays(3)));
		Image onPost = storedImage(now.minus(Duration.ofDays(30)));
		steps.attachImage(post, 1, onPost.id());
		long room = chatSteps.insertDirectRoom(author, other, now.minus(Duration.ofDays(30)));
		long message = chatSteps.text(room, author, "사진 보내요", now.minus(Duration.ofDays(30)));
		Image onMessage = storedImage(now.minus(Duration.ofDays(30)));
		chatSteps.attachImage(message, 1, onMessage.id());

		job.runOnce();

		assertThat(imageExists(detached.id())).isFalse();
		assertThat(Files.exists(detached.file())).isFalse();
		assertThat(imageExists(onPost.id())).isTrue();
		assertThat(Files.exists(onPost.file())).isTrue();
		assertThat(imageExists(onMessage.id())).isTrue();
		assertThat(Files.exists(onMessage.file())).isTrue();
	}

	@Test
	void 파일이_이미_없어도_줄은_지워지고_작업은_계속된다() throws IOException {
		Image missing = storedImage(now.minus(Duration.ofDays(2)));
		Files.delete(missing.file());
		Image present = storedImage(now.minus(Duration.ofDays(2)));

		assertThat(job.runOnce()).isTrue();

		assertThat(imageExists(missing.id())).isFalse();
		assertThat(imageExists(present.id())).isFalse();
		assertThat(Files.exists(present.file())).isFalse();
	}

	@Test
	void 다른_서버가_잠금을_잡고_있으면_아무것도_지우지_않는다() {
		long post = deletedPost(now.minus(Duration.ofDays(30)));
		Image image = storedImage(now.minus(Duration.ofDays(2)));
		redis.opsForValue().set(LOCK_KEY, "other-server", Duration.ofMinutes(5));

		assertThat(job.runOnce()).isFalse();

		assertThat(count("SELECT COUNT(*) FROM post_version WHERE post_id = ?", post)).isEqualTo(1);
		assertThat(imageExists(image.id())).isTrue();
		assertThat(Files.exists(image.file())).isTrue();
		assertThat(redis.opsForValue().get(LOCK_KEY)).isEqualTo("other-server");
	}

	@Test
	void 작업이_끝나면_잠금을_풀어_다음_실행이_돈다() {
		assertThat(job.runOnce()).isTrue();
		assertThat(redis.hasKey(LOCK_KEY)).isFalse();

		Image image = storedImage(now.minus(Duration.ofDays(2)));
		assertThat(job.runOnce()).isTrue();
		assertThat(imageExists(image.id())).isFalse();
	}

	@Test
	void 천_장이_넘어도_한_번에_모두_지운다() {
		List<Object[]> rows = new ArrayList<>();
		LocalDateTime old = LocalDateTime.ofInstant(now.minus(Duration.ofDays(2)), ZoneOffset.UTC);
		for (int i = 0; i < 1_001; i++) {
			rows.add(new Object[] { author, url("2026/11/" + UUID.randomUUID() + ".webp"), old });
		}
		jdbcTemplate.batchUpdate("INSERT INTO uploaded_image (uploader_id, image_url, created_at) VALUES (?, ?, ?)",
				rows);

		job.runOnce();

		assertThat(count("SELECT COUNT(*) FROM uploaded_image WHERE uploader_id = ?", author)).isZero();
	}

	@Test
	void 저장소는_자기_주소의_파일만_지우고_밖이나_폴더나_다른_서버의_주소는_지우지_않는다() throws IOException {
		Image mine = storedImage(now);
		assertThat(imageStorage.delete(mine.url())).isTrue();
		assertThat(Files.exists(mine.file())).isFalse();
		assertThat(imageStorage.delete(mine.url())).isFalse();

		Path outside = TestcontainersConfiguration.IMAGE_DIR.resolveSibling("itting-outside-" + UUID.randomUUID());
		Files.writeString(outside, "secret");
		try {
			assertThat(imageStorage.delete(url("../" + outside.getFileName()))).isFalse();
			assertThat(Files.exists(outside)).isTrue();
		}
		finally {
			Files.deleteIfExists(outside);
		}

		Image kept = storedImage(now);
		Path folder = kept.file().getParent();
		String folderKey = TestcontainersConfiguration.IMAGE_DIR.relativize(folder).toString().replace('\\', '/');
		assertThat(imageStorage.delete(url(folderKey))).isFalse();
		assertThat(imageStorage.delete(url(""))).isFalse();
		assertThat(imageStorage.delete(kept.url().replace(imageProperties.publicBaseUrl(), "https://other.example")))
				.isFalse();
		assertThat(Files.exists(kept.file())).isTrue();
	}

	private record Image(long id, String url, Path file) {
	}

	/** 저장소에 파일을 두고 그 주소로 올린 사진 줄을 만든다. */
	private Image storedImage(Instant uploadedAt) {
		String key = "2026/11/" + UUID.randomUUID() + ".webp";
		Path file = TestcontainersConfiguration.IMAGE_DIR.resolve(key);
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, new byte[] { 1, 2, 3 });
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		String url = url(key);
		long id = steps.uploadImage(author, url);
		jdbcTemplate.update("UPDATE uploaded_image SET created_at = ? WHERE id = ?", CommunitySteps.utc(uploadedAt),
				id);
		return new Image(id, url, file);
	}

	private String url(String key) {
		return imageProperties.publicBaseUrl() + "/images/" + key;
	}

	private long deletedPost(Instant deletedAt) {
		long post = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", deletedAt.minus(Duration.ofDays(1)));
		jdbcTemplate.update("UPDATE post SET deleted_at = ? WHERE id = ?", CommunitySteps.utc(deletedAt), post);
		return post;
	}

	private long deletedComment(long post, Long parentId, Instant deletedAt) {
		long comment = steps.comment(post, other, parentId, "DELETED");
		jdbcTemplate.update("UPDATE comment SET deleted_at = ? WHERE id = ?", CommunitySteps.utc(deletedAt), comment);
		return comment;
	}

	private boolean imageExists(long imageId) {
		return count("SELECT COUNT(*) FROM uploaded_image WHERE id = ?", imageId) > 0;
	}

	private long count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}

}
