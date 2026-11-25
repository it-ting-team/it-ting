package com.itting.community.service.image;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * 사진에 든 메타데이터(촬영 위치가 담기는 EXIF · XMP와 글자 정보)를 걷어낸다(KAN-53 D). 그림 데이터는 다시 인코딩하지 않고
 * 그대로 둔다.
 *
 * <ul>
 * <li>JPEG: APP1(EXIF · XMP), APP13(IPTC), COM(주석) 구간을 뺀다. 사진의 회전 방향(EXIF Orientation)은 화면에
 * 바르게 보이는 데 필요하므로, 원본에 있으면 그 값 하나만 담은 EXIF를 새로 넣는다.</li>
 * <li>PNG: {@code eXIf}, {@code tEXt}, {@code iTXt}, {@code zTXt} 청크를 뺀다.</li>
 * <li>WebP: {@code EXIF}, {@code XMP } 청크를 빼고, {@code VP8X}의 EXIF · XMP 표시를 끈다.</li>
 * </ul>
 *
 * 메타데이터를 빼면서 형식별 필수 구조(JPEG의 프레임 · 스캔 · EOI, PNG의 IHDR · IDAT · IEND와 CRC, WebP의 RIFF 크기와
 * 그림 청크)도 확인한다. 구조가 깨졌거나 그림이 없으면 {@link IllegalArgumentException}을 던진다. 스캔 사이와 끝(EOI · IEND)
 * 뒤에 붙은 바이트도 거른다.
 */
public final class ImageMetadataStripper {

	private static final int JPEG_SOS = 0xDA;

	private static final int JPEG_EOI = 0xD9;

	private static final int JPEG_APP0 = 0xE0;

	private static final Set<Integer> JPEG_DROPPED = Set.of(0xE1, 0xED, 0xFE);

	/** 그림의 크기와 방식을 적는 구간(SOF0 ~ SOF15, DHT · JPG · DAC 제외). SOS 앞에 하나가 있어야 한다. */
	private static final Set<Integer> JPEG_FRAMES = Set.of(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA,
			0xCB, 0xCD, 0xCE, 0xCF);

	private static final byte[] EXIF_HEADER = { 'E', 'x', 'i', 'f', 0, 0 };

	private static final int EXIF_ORIENTATION_TAG = 0x0112;

	private static final Set<String> PNG_DROPPED = Set.of("eXIf", "tEXt", "iTXt", "zTXt");

	private static final Set<String> WEBP_DROPPED = Set.of("EXIF", "XMP ");

	private static final Set<String> WEBP_FIRST = Set.of("VP8 ", "VP8L", "VP8X");

	private static final Set<String> WEBP_IMAGES = Set.of("VP8 ", "VP8L", "ANMF");

	/** VP8X 표시 바이트의 EXIF(0x08) · XMP(0x04) 비트. */
	private static final int VP8X_METADATA_FLAGS = 0x08 | 0x04;

	private ImageMetadataStripper() {
	}

	public static byte[] strip(byte[] bytes, ImageFormat format) {
		return switch (format) {
			case JPEG -> stripJpeg(bytes);
			case PNG -> stripPng(bytes);
			case WEBP -> stripWebp(bytes);
		};
	}

	private static byte[] stripJpeg(byte[] b) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
		out.write(b, 0, 2);
		// 회전 방향은 EXIF(APP1)가 어느 구간 뒤에 있든 남기도록, 쓰기 전에 먼저 찾아 둔다.
		Integer orientation = findOrientation(b);
		boolean orientationWritten = false;
		boolean sawFrame = false;
		boolean sawScan = false;
		int pos = 2;
		while (true) {
			require(pos + 1 < b.length && (b[pos] & 0xFF) == 0xFF);
			int marker = b[pos + 1] & 0xFF;
			if (marker == 0xFF) {
				pos++;
				continue;
			}
			if (marker == JPEG_EOI) {
				// 그림 데이터(SOS)가 있어야 하고, EOI 뒤에 붙은 바이트는 버린다.
				require(sawScan);
				out.write(b, pos, 2);
				return out.toByteArray();
			}
			if ((marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
				out.write(b, pos, 2);
				pos += 2;
				continue;
			}
			require(pos + 3 < b.length);
			int length = u16be(b, pos + 2);
			int end = pos + 2 + length;
			require(length >= 2 && end <= b.length);
			if (JPEG_FRAMES.contains(marker)) {
				sawFrame = true;
			}
			if (marker == JPEG_SOS) {
				require(sawFrame);
			}
			if (!JPEG_DROPPED.contains(marker)) {
				// JFIF(APP0)는 SOI 바로 뒤에 두어야 하므로 회전 방향은 APP0 뒤, 다른 구간 앞에 넣는다.
				if (marker != JPEG_APP0 && !orientationWritten) {
					writeOrientation(out, orientation);
					orientationWritten = true;
				}
				out.write(b, pos, end - pos);
			}
			pos = end;
			if (marker == JPEG_SOS) {
				// 스캔 데이터는 다음 표시(FF 00 이스케이프와 재시작 표시 RSTn이 아닌 것)까지다. 그 뒤의 구간도 위와 같이 거른다.
				sawScan = true;
				int scanStart = pos;
				while (true) {
					require(pos + 1 < b.length);
					if ((b[pos] & 0xFF) == 0xFF) {
						int next = b[pos + 1] & 0xFF;
						if (next == 0x00 || (next >= 0xD0 && next <= 0xD7)) {
							pos += 2;
							continue;
						}
						if (next != 0xFF) {
							break;
						}
					}
					pos++;
				}
				out.write(b, scanStart, pos - scanStart);
			}
		}
	}

	/**
	 * 첫 스캔(SOS) 앞의 구간 가운데 처음 나오는 EXIF APP1에서 회전 방향을 읽는다. 구조를 검사하지 않고, 읽을 수 없으면 null이다
	 * (구조는 {@link #stripJpeg}가 검사한다).
	 */
	private static Integer findOrientation(byte[] b) {
		int pos = 2;
		while (pos + 3 < b.length && (b[pos] & 0xFF) == 0xFF) {
			int marker = b[pos + 1] & 0xFF;
			if (marker == 0xFF) {
				pos++;
				continue;
			}
			if (marker == JPEG_SOS || marker == JPEG_EOI) {
				return null;
			}
			if ((marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
				pos += 2;
				continue;
			}
			int end = pos + 2 + u16be(b, pos + 2);
			if (end > b.length) {
				return null;
			}
			if (marker == 0xE1) {
				Integer orientation = exifOrientation(b, pos + 4, end);
				if (orientation != null) {
					return orientation;
				}
			}
			pos = end;
		}
		return null;
	}

	/**
	 * EXIF APP1 구간에서 회전 방향(IFD0의 0x0112)을 읽는다.
	 *
	 * @return 없거나 읽을 수 없으면 null
	 */
	private static Integer exifOrientation(byte[] b, int start, int end) {
		if (end - start < EXIF_HEADER.length + 8
				|| !Arrays.equals(b, start, start + EXIF_HEADER.length, EXIF_HEADER, 0, EXIF_HEADER.length)) {
			return null;
		}
		int tiff = start + EXIF_HEADER.length;
		boolean little;
		if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
			little = true;
		}
		else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
			little = false;
		}
		else {
			return null;
		}
		long ifd = tiff + u32(b, tiff + 4, little);
		if (ifd + 2 > end) {
			return null;
		}
		int count = u16(b, (int) ifd, little);
		for (int i = 0; i < count; i++) {
			int entry = (int) ifd + 2 + i * 12;
			if (entry + 12 > end) {
				return null;
			}
			if (u16(b, entry, little) == EXIF_ORIENTATION_TAG) {
				int value = u16(b, entry + 8, little);
				return value >= 1 && value <= 8 ? value : null;
			}
		}
		return null;
	}

	/** 회전 방향 하나만 담은 EXIF APP1 구간. 방향이 없거나 기본값(1)이면 쓰지 않는다. */
	private static void writeOrientation(ByteArrayOutputStream out, Integer orientation) {
		if (orientation == null || orientation == 1) {
			return;
		}
		byte[] segment = {
				(byte) 0xFF, (byte) 0xE1, 0, 34,
				'E', 'x', 'i', 'f', 0, 0,
				// TIFF 머리(빅 엔디언), IFD0은 8바이트 뒤
				'M', 'M', 0, 0x2A, 0, 0, 0, 8,
				// 항목 1개: 0x0112, SHORT(3), 개수 1, 값
				0, 1,
				0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation.intValue(), 0, 0,
				// 다음 IFD 없음
				0, 0, 0, 0 };
		out.write(segment, 0, segment.length);
	}

	/** 첫 청크는 IHDR(13바이트)이고, 청크마다 CRC가 맞고, IDAT가 하나 이상이고, IEND로 끝나야 한다. IEND 뒤의 바이트는 버린다. */
	private static byte[] stripPng(byte[] b) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
		out.write(b, 0, 8);
		boolean first = true;
		boolean sawData = false;
		int pos = 8;
		while (true) {
			require(pos + 12 <= b.length);
			long length = u32(b, pos, false);
			require(length <= b.length - pos - 12);
			int end = pos + 12 + (int) length;
			String type = new String(b, pos + 4, 4, StandardCharsets.US_ASCII);
			CRC32 crc = new CRC32();
			crc.update(b, pos + 4, 4 + (int) length);
			require(crc.getValue() == u32(b, end - 4, false));
			if (first) {
				require(type.equals("IHDR") && length == 13);
				first = false;
			}
			if (type.equals("IDAT")) {
				sawData = true;
			}
			if (!PNG_DROPPED.contains(type)) {
				out.write(b, pos, end - pos);
			}
			pos = end;
			if (type.equals("IEND")) {
				require(sawData);
				return out.toByteArray();
			}
		}
	}

	/**
	 * RIFF 크기가 파일 길이와 맞고, 첫 청크가 {@code VP8 } · {@code VP8L} · {@code VP8X}이고, 그림 청크({@code VP8 } ·
	 * {@code VP8L} · 움직이는 그림의 {@code ANMF})가 하나 이상 있어야 한다.
	 */
	private static byte[] stripWebp(byte[] b) {
		require(b.length >= 12 && u32(b, 4, true) + 8 == b.length);
		ByteArrayOutputStream body = new ByteArrayOutputStream(b.length);
		boolean sawImage = false;
		int pos = 12;
		while (pos < b.length) {
			require(pos + 8 <= b.length);
			String type = new String(b, pos, 4, StandardCharsets.US_ASCII);
			long size = u32(b, pos + 4, true);
			require(size <= b.length);
			int end = pos + 8 + (int) size + (int) (size & 1);
			require(end <= b.length || (end == b.length + 1 && (size & 1) == 1));
			end = Math.min(end, b.length);
			if (pos == 12) {
				require(WEBP_FIRST.contains(type));
			}
			if (WEBP_IMAGES.contains(type)) {
				sawImage = true;
			}
			if (!WEBP_DROPPED.contains(type)) {
				byte[] chunk = Arrays.copyOfRange(b, pos, end);
				if (type.equals("VP8X") && size > 0) {
					chunk[8] = (byte) (chunk[8] & ~VP8X_METADATA_FLAGS);
				}
				body.write(chunk, 0, chunk.length);
			}
			pos = end;
		}
		require(sawImage);
		byte[] chunks = body.toByteArray();
		ByteArrayOutputStream out = new ByteArrayOutputStream(chunks.length + 12);
		long riffSize = chunks.length + 4L;
		out.write(new byte[] { 'R', 'I', 'F', 'F', (byte) riffSize, (byte) (riffSize >> 8), (byte) (riffSize >> 16),
				(byte) (riffSize >> 24), 'W', 'E', 'B', 'P' }, 0, 12);
		out.write(chunks, 0, chunks.length);
		return out.toByteArray();
	}

	private static void require(boolean condition) {
		if (!condition) {
			throw new IllegalArgumentException("사진 파일의 구조가 올바르지 않습니다.");
		}
	}

	private static int u16be(byte[] b, int pos) {
		return ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
	}

	private static int u16(byte[] b, int pos, boolean little) {
		return little ? (b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8) : u16be(b, pos);
	}

	private static long u32(byte[] b, int pos, boolean little) {
		if (little) {
			return (b[pos] & 0xFFL) | ((b[pos + 1] & 0xFFL) << 8) | ((b[pos + 2] & 0xFFL) << 16)
					| ((b[pos + 3] & 0xFFL) << 24);
		}
		return ((b[pos] & 0xFFL) << 24) | ((b[pos + 1] & 0xFFL) << 16) | ((b[pos + 2] & 0xFFL) << 8)
				| (b[pos + 3] & 0xFFL);
	}

}
