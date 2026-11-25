package com.itting.community.service.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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

}
