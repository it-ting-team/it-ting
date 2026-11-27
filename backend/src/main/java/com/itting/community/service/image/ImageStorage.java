package com.itting.community.service.image;

/**
 * 사진 파일을 두는 저장소. 지금은 서버 디스크({@link LocalDiskImageStorage})이고, 외부 저장소(GCS 등)로 바꿀 때는 이
 * 구현만 더한다. API와 DB({@code uploaded_image.image_url})는 그대로다(KAN-53 B).
 */
public interface ImageStorage {

	/**
	 * 사진을 새 이름으로 저장한다. 같은 이름의 파일을 덮어쓰지 않는다.
	 *
	 * @return 프론트엔드가 그대로 쓰는 사진의 주소
	 */
	String store(byte[] content, ImageFormat format);

	/**
	 * {@link #store}가 준 주소의 파일을 지운다. 이 저장소의 주소가 아니거나 파일이 이미 없으면 아무것도 하지 않는다
	 * (KAN-66).
	 *
	 * @return 파일을 지웠으면 true
	 * @throws java.io.UncheckedIOException 파일이 있는데 지우지 못했을 때
	 */
	boolean delete(String imageUrl);

}
