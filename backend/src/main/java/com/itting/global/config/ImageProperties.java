package com.itting.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 올린 사진을 두는 곳과 사진 주소({@code docs/api/08-community.md}의 사진 올리기). 없으면 서버가 뜨지 않는다.
 *
 * @param storageDir {@code itting.images.storage-dir}. 사진 파일을 두는 서버의 폴더. 예: {@code ./.local-images}
 * @param publicBaseUrl {@code itting.images.public-base-url}. 사진 주소의 앞부분. 끝에 {@code /}를 붙이지 않는다.
 *        사진은 {@code <publicBaseUrl>/images/2026/11/<UUID>.webp}로 받는다. 예: {@code http://localhost:8080}
 */
@Validated
@ConfigurationProperties("itting.images")
public record ImageProperties(@NotBlank String storageDir, @NotBlank String publicBaseUrl) {
}
