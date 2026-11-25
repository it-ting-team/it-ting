package com.itting.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

/**
 * 사진 올리기 테스트에 쓰는 작은 사진. 촬영 위치처럼 지워져야 하는 메타데이터에는 {@link #SECRET}을 넣어, 저장한 파일에 그 글자가
 * 남았는지로 확인한다.
 */
public final class TestImages {

	/** 메타데이터에 넣는 글자. 저장한 사진에 남으면 안 된다. */
	public static final String SECRET = "GPS-37.5665N-126.9780E";

	private TestImages() {
	}

	/** 2x2 JPEG. 메타데이터가 없다. */
	public static byte[] jpeg() {
		return encode("jpg");
	}

	/** 2x2 PNG. 메타데이터가 없다. */
	public static byte[] png() {
		return encode("png");
	}

	/**
	 * EXIF(회전 방향과 위치 글자), XMP, 주석이 든 JPEG.
	 *
	 * @param orientation EXIF의 회전 방향(1 ~ 8). null이면 넣지 않는다
	 */
	public static byte[] jpegWithMetadata(Integer orientation) {
		byte[] plain = jpeg();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(plain, 0, 2);
		writeJpegSegment(out, 0xE1, exif(orientation));
		writeJpegSegment(out, 0xE1, ("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>" + SECRET + "</x:xmpmeta>")
				.getBytes(StandardCharsets.UTF_8));
		writeJpegSegment(out, 0xFE, ("comment " + SECRET).getBytes(StandardCharsets.UTF_8));
		out.write(plain, 2, plain.length - 2);
		return out.toByteArray();
	}

	/** {@code tEXt} 청크가 든 PNG. */
	public static byte[] pngWithText() {
		byte[] plain = png();
		// 서명(8) + IHDR(4 + 4 + 13 + 4) 뒤에 넣는다.
		int afterHeader = 8 + 25;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(plain, 0, afterHeader);
		byte[] text = ("Comment\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1);
		out.writeBytes(ByteBuffer.allocate(4).putInt(text.length).array());
		byte[] typeAndData = new byte[4 + text.length];
		System.arraycopy("tEXt".getBytes(StandardCharsets.US_ASCII), 0, typeAndData, 0, 4);
		System.arraycopy(text, 0, typeAndData, 4, text.length);
		out.writeBytes(typeAndData);
		CRC32 crc = new CRC32();
		crc.update(typeAndData);
		out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
		out.write(plain, afterHeader, plain.length - afterHeader);
		return out.toByteArray();
	}

	/**
	 * 구조만 맞춘 WebP. {@code VP8X}(EXIF · XMP 표시 켬), 그림 청크({@code VP8L}), {@code EXIF}, {@code XMP }를 담는다.
	 * 그림 데이터는 실제 사진이 아니다(서버는 다시 인코딩하지 않으므로 구조만 본다).
	 */
	public static byte[] webpWithMetadata() {
		ByteArrayOutputStream chunks = new ByteArrayOutputStream();
		writeWebpChunk(chunks, "VP8X", new byte[] { 0x0C, 0, 0, 0, 1, 0, 0, 1, 0, 0 });
		writeWebpChunk(chunks, "VP8L", new byte[] { 0x2F, 1, 0, 0, 0 });
		writeWebpChunk(chunks, "EXIF", ("Exif" + SECRET).getBytes(StandardCharsets.US_ASCII));
		writeWebpChunk(chunks, "XMP ", ("<x:xmpmeta>" + SECRET + "</x:xmpmeta>").getBytes(StandardCharsets.UTF_8));
		return riff(chunks.toByteArray());
	}

	/** 메타데이터가 없는 WebP 구조. */
	public static byte[] webp() {
		ByteArrayOutputStream chunks = new ByteArrayOutputStream();
		writeWebpChunk(chunks, "VP8L", new byte[] { 0x2F, 1, 0, 0, 0 });
		return riff(chunks.toByteArray());
	}

	/** GIF의 앞부분. 받지 않는 형식이다. */
	public static byte[] gif() {
		return "GIF89a\1\0\1\0\0\0\0;".getBytes(StandardCharsets.ISO_8859_1);
	}

	/** EXIF 안의 정수 값을 바이트에서 찾지 않고, 들어 있는 글자로 확인한다. */
	public static boolean containsSecret(byte[] bytes) {
		return new String(bytes, StandardCharsets.ISO_8859_1).contains(SECRET);
	}

	private static byte[] encode(String format) {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, 0xFF0000);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, format, out);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	/** 리틀 엔디언 TIFF. IFD0에 회전 방향과 위치 글자(ImageDescription)를 둔다. */
	private static byte[] exif(Integer orientation) {
		byte[] secret = (SECRET + "\0").getBytes(StandardCharsets.US_ASCII);
		int entries = orientation == null ? 1 : 2;
		int ifdSize = 2 + entries * 12 + 4;
		ByteBuffer tiff = ByteBuffer.allocate(8 + ifdSize + secret.length).order(ByteOrder.LITTLE_ENDIAN);
		tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
		tiff.putShort((short) entries);
		// ImageDescription(0x010E), ASCII(2), 글자 수, 값의 자리
		tiff.putShort((short) 0x010E).putShort((short) 2).putInt(secret.length).putInt(8 + ifdSize);
		if (orientation != null) {
			tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort(orientation.shortValue())
					.putShort((short) 0);
		}
		tiff.putInt(0);
		tiff.put(secret);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { 'E', 'x', 'i', 'f', 0, 0 });
		out.writeBytes(tiff.array());
		return out.toByteArray();
	}

	private static void writeJpegSegment(ByteArrayOutputStream out, int marker, byte[] data) {
		int length = data.length + 2;
		out.write(0xFF);
		out.write(marker);
		out.write(length >> 8);
		out.write(length & 0xFF);
		out.writeBytes(data);
	}

	private static void writeWebpChunk(ByteArrayOutputStream out, String type, byte[] data) {
		out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array());
		out.writeBytes(data);
		if (data.length % 2 == 1) {
			out.write(0);
		}
	}

	private static byte[] riff(byte[] chunks) {
		ByteBuffer riff = ByteBuffer.allocate(12 + chunks.length).order(ByteOrder.LITTLE_ENDIAN);
		riff.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(chunks.length + 4)
				.put("WEBP".getBytes(StandardCharsets.US_ASCII)).put(chunks);
		return riff.array();
	}

}
