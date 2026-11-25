package com.itting.global.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 올린 사진을 {@code GET /images/**}로 준다. 로그인 없이 받을 수 있고, 파일 이름이 UUID라 주소를 모르면 찾을 수 없다
 * (KAN-53 C). 한 번 올린 파일은 바뀌지 않으므로 오래 캐시한다.
 *
 * <p>정적 파일은 끄고({@code spring.web.resources.add-mappings: false}) 이 주소만 연다.
 */
@Configuration
@EnableConfigurationProperties(ImageProperties.class)
public class ImageResourceConfig implements WebMvcConfigurer {

	public static final String URL_PATH = "/images/";

	private final ImageProperties imageProperties;

	public ImageResourceConfig(ImageProperties imageProperties) {
		this.imageProperties = imageProperties;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = Path.of(imageProperties.storageDir()).toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler(URL_PATH + "**")
				.addResourceLocations(location.endsWith("/") ? location : location + "/")
				.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
	}

}
