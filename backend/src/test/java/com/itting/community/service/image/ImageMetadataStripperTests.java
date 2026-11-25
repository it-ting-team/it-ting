package com.itting.community.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import com.itting.support.TestImages;
import org.junit.jupiter.api.Test;

/**
 * 사진의 메타데이터 걷어내기(KAN-53 D)와 형식 알아내기. DB나 서버 없이 바이트만 본다.
 */
class ImageMetadataStripperTests {

	@Test
	void 형식은_파일_앞부분의_바이트로_알아낸다() {
		assertThat(ImageFormat.detect(TestImages.jpeg())).contains(ImageFormat.JPEG);
		assertThat(ImageFormat.detect(TestImages.png())).contains(ImageFormat.PNG);
		assertThat(ImageFormat.detect(TestImages.webp())).contains(ImageFormat.WEBP);
		assertThat(ImageFormat.detect(TestImages.gif())).isEmpty();
		assertThat(ImageFormat.detect("hello".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(ImageFormat.detect(new byte[0])).isEmpty();
	}

	@Test
	void JPEG의_EXIF_XMP_주석을_빼고_회전_방향만_남긴다() throws Exception {
		byte[] original = TestImages.jpegWithMetadata(6);
		assertThat(TestImages.containsSecret(original)).isTrue();

		byte[] stripped = ImageMetadataStripper.strip(original, ImageFormat.JPEG);

		assertThat(TestImages.containsSecret(stripped)).isFalse();
		List<byte[]> app1 = jpegSegments(stripped, 0xE1);
		assertThat(app1).hasSize(1);
		// "Exif\0\0" + 빅 엔디언 TIFF, 항목 1개(0x0112, SHORT, 1, 6)
		assertThat(app1.getFirst()).containsExactly('E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 0x2A, 0, 0, 0, 8, 0, 1,
				0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0);
		// JFIF(APP0)는 SOI 바로 뒤에 남는다.
		assertThat(stripped[2] & 0xFF).isEqualTo(0xFF);
		assertThat(stripped[3] & 0xFF).isEqualTo(0xE0);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(stripped));
		assertThat(image.getWidth()).isEqualTo(2);
		assertThat(image.getHeight()).isEqualTo(2);
	}

	@Test
	void JPEG에_회전_방향이_없으면_EXIF를_남기지_않는다() {
		byte[] stripped = ImageMetadataStripper.strip(TestImages.jpegWithMetadata(null), ImageFormat.JPEG);

		assertThat(TestImages.containsSecret(stripped)).isFalse();
		assertThat(jpegSegments(stripped, 0xE1)).isEmpty();
	}

	@Test
	void 메타데이터가_없는_JPEG는_그대로다() {
		byte[] plain = TestImages.jpeg();

		assertThat(ImageMetadataStripper.strip(plain, ImageFormat.JPEG)).isEqualTo(plain);
	}

	@Test
	void 프로그레시브_JPEG의_스캔_사이와_EOI_뒤의_메타데이터도_뺀다() throws Exception {
		byte[] original = TestImages.progressiveJpegWithMetadataBetweenScans();
		assertThat(TestImages.containsSecret(original)).isTrue();

		byte[] stripped = ImageMetadataStripper.strip(original, ImageFormat.JPEG);

		assertThat(TestImages.containsSecret(stripped)).isFalse();
		assertThat(stripped).isEqualTo(TestImages.progressiveJpeg());
		assertThat(ImageIO.read(new ByteArrayInputStream(stripped)).getWidth()).isEqualTo(16);
	}

	@Test
	void PNG의_글자_청크를_뺀다() throws Exception {
		byte[] original = TestImages.pngWithText();
		assertThat(TestImages.containsSecret(original)).isTrue();

		byte[] stripped = ImageMetadataStripper.strip(original, ImageFormat.PNG);

		assertThat(TestImages.containsSecret(stripped)).isFalse();
		assertThat(stripped).isEqualTo(TestImages.png());
		assertThat(ImageIO.read(new ByteArrayInputStream(stripped)).getWidth()).isEqualTo(2);
	}

	@Test
	void WebP의_EXIF_XMP_청크를_빼고_VP8X_표시를_끄고_크기를_맞춘다() {
		byte[] original = TestImages.webpWithMetadata();
		assertThat(TestImages.containsSecret(original)).isTrue();

		byte[] stripped = ImageMetadataStripper.strip(original, ImageFormat.WEBP);

		assertThat(TestImages.containsSecret(stripped)).isFalse();
		ByteBuffer buffer = ByteBuffer.wrap(stripped).order(ByteOrder.LITTLE_ENDIAN);
		assertThat(buffer.getInt(4)).isEqualTo(stripped.length - 8);
		List<String> chunks = new ArrayList<>();
		int pos = 12;
		while (pos < stripped.length) {
			String type = new String(stripped, pos, 4, StandardCharsets.US_ASCII);
			int size = buffer.getInt(pos + 4);
			chunks.add(type);
			if (type.equals("VP8X")) {
				assertThat(stripped[pos + 8]).isZero();
			}
			pos += 8 + size + (size & 1);
		}
		assertThat(chunks).containsExactly("VP8X", "VP8L");
	}

	@Test
	void 구조가_깨진_파일은_받지_않는다() {
		byte[] jpeg = TestImages.jpeg();
		byte[] png = TestImages.png();
		byte[] webp = TestImages.webpWithMetadata();

		assertThatThrownBy(() -> ImageMetadataStripper.strip(Arrays.copyOf(jpeg, 20), ImageFormat.JPEG))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ImageMetadataStripper.strip(Arrays.copyOf(png, 20), ImageFormat.PNG))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ImageMetadataStripper.strip(Arrays.copyOf(webp, 20), ImageFormat.WEBP))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 그림이_없거나_끝이_잘린_파일은_받지_않는다() {
		byte[] jpeg = TestImages.jpeg();
		byte[] png = TestImages.png();
		byte[] webp = TestImages.webp();

		// JPEG: EOI가 없음(스캔 데이터 중간에서 잘림), SOS 없이 EOI
		assertThatThrownBy(() -> ImageMetadataStripper.strip(Arrays.copyOf(jpeg, jpeg.length - 2), ImageFormat.JPEG))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ImageMetadataStripper.strip(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
				(byte) 0xD9 }, ImageFormat.JPEG)).isInstanceOf(IllegalArgumentException.class);
		// PNG: 마지막 청크(IEND)가 없음, CRC가 틀림
		assertThatThrownBy(() -> ImageMetadataStripper.strip(Arrays.copyOf(png, png.length - 12), ImageFormat.PNG))
				.isInstanceOf(IllegalArgumentException.class);
		byte[] badCrc = png.clone();
		badCrc[8 + 8 + 13] ^= 0x01;
		assertThatThrownBy(() -> ImageMetadataStripper.strip(badCrc, ImageFormat.PNG))
				.isInstanceOf(IllegalArgumentException.class);
		// WebP: 머리만 있음, RIFF 크기와 파일 길이가 다름
		assertThatThrownBy(() -> ImageMetadataStripper.strip(TestImages.webpHeaderOnly(), ImageFormat.WEBP))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ImageMetadataStripper.strip(Arrays.copyOf(webp, webp.length + 4), ImageFormat.WEBP))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** SOS 앞까지의 구간 가운데 이 표시의 데이터(길이 2바이트 뒤). */
	private static List<byte[]> jpegSegments(byte[] jpeg, int marker) {
		List<byte[]> found = new ArrayList<>();
		int pos = 2;
		while (pos + 4 <= jpeg.length && (jpeg[pos + 1] & 0xFF) != 0xDA) {
			int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
			if ((jpeg[pos + 1] & 0xFF) == marker) {
				found.add(Arrays.copyOfRange(jpeg, pos + 4, pos + 2 + length));
			}
			pos += 2 + length;
		}
		return found;
	}

}
