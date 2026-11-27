package com.itting.community.service.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import com.itting.global.config.ImageProperties;
import com.itting.global.config.ImageResourceConfig;
import org.springframework.stereotype.Component;

/**
 * 사진을 서버 디스크({@code itting.images.storage-dir})에 둔다. 파일은 {@code <연>/<월>/<UUID>.<확장자>}(UTC)에 두고,
 * {@link ImageResourceConfig}가 {@code /images/**}로 준다.
 */
@Component
public class LocalDiskImageStorage implements ImageStorage {

	private final Path root;

	private final String publicBaseUrl;

	private final Clock clock;

	public LocalDiskImageStorage(ImageProperties properties, Clock clock) {
		this.root = Path.of(properties.storageDir()).toAbsolutePath().normalize();
		this.publicBaseUrl = properties.publicBaseUrl();
		this.clock = clock;
	}

	@Override
	public String store(byte[] content, ImageFormat format) {
		YearMonth month = YearMonth.now(clock.withZone(ZoneOffset.UTC));
		String key = "%04d/%02d/%s.%s".formatted(month.getYear(), month.getMonthValue(), UUID.randomUUID(),
				format.extension());
		Path target = root.resolve(key);
		try {
			Files.createDirectories(target.getParent());
			// 다 쓰기 전의 파일이 주소로 나가지 않게 임시 파일에 쓴 뒤 옮긴다.
			Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
			try {
				Files.write(temp, content);
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
			}
			finally {
				Files.deleteIfExists(temp);
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException("사진을 저장하지 못했습니다: " + key, e);
		}
		return publicBaseUrl + ImageResourceConfig.URL_PATH + key;
	}

	/**
	 * 주소가 {@code <publicBaseUrl>/images/}로 시작하고, 가리키는 곳이 {@code root} 안의 보통 파일일 때만 지운다.
	 * {@code ../}로 밖을 가리키거나 폴더를 가리키는 주소는 지우지 않는다.
	 */
	@Override
	public boolean delete(String imageUrl) {
		String prefix = publicBaseUrl + ImageResourceConfig.URL_PATH;
		if (imageUrl == null || !imageUrl.startsWith(prefix)) {
			return false;
		}
		Path target;
		try {
			target = root.resolve(imageUrl.substring(prefix.length())).normalize();
		}
		catch (InvalidPathException e) {
			return false;
		}
		if (!target.startsWith(root) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
			return false;
		}
		try {
			return Files.deleteIfExists(target);
		}
		catch (IOException e) {
			throw new UncheckedIOException("사진을 지우지 못했습니다: " + imageUrl, e);
		}
	}

}
