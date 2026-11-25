package com.itting.global.security;

import java.time.Duration;
import java.util.List;

import com.itting.global.config.FrontendProperties;
import com.itting.global.error.CommonErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 로그인 검사, CORS, CSRF 설정({@code docs/api-convention.md}의 "로그인").
 *
 * <ul>
 * <li>로그인 없이 부를 수 있는 주소는 {@link #PUBLIC_API}에 적은 것뿐이다. 나머지 {@code /api/**}는 로그인이 필요하다.
 * 새 API가 로그인 없이 불려야 하면 여기에 더한다.</li>
 * <li>CSRF 토큰은 쓰지 않는다. 세션 쿠키가 {@code SameSite=Lax}라 다른 사이트에서 보낸 POST · DELETE에는 쿠키가 실리지 않는다.
 * 그래서 프론트엔드와 백엔드는 같은 사이트(같은 주 도메인)에 둔다.</li>
 * <li>CORS는 설정 {@code itting.cors.allowed-origins}의 출처만 쿠키와 함께 허용한다.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

	/**
	 * 로그인 없이 부를 수 있는 API. 메서드를 적지 않은 주소는 모든 메서드를 연다(허용하지 않는 메서드는 405로 답한다).
	 * 같은 주소에 로그인이 필요한 메서드가 생길 수 있으면 메서드를 적는다.
	 */
	static final List<PublicApi> PUBLIC_API = List.of(
			PublicApi.anyMethod("/api/v1/campuses"),
			PublicApi.anyMethod("/api/v1/generations"),
			PublicApi.anyMethod("/api/v1/sessions"),
			PublicApi.anyMethod("/api/v1/email-verifications"),
			PublicApi.anyMethod("/api/v1/email-verifications/check"),
			PublicApi.anyMethod("/api/v1/ssafy-verifications"),
			PublicApi.of(HttpMethod.POST, "/api/v1/users"),
			PublicApi.of(HttpMethod.POST, "/api/v1/password-resets"),
			PublicApi.of(HttpMethod.GET, "/api/v1/password-resets/*"),
			PublicApi.of(HttpMethod.POST, "/api/v1/password-resets/*/complete"),
			// Google 로그인. 설정의 Google 확인(/api/v1/oauth/google/verify)은 로그인이 필요해 여기 두지 않는다.
			PublicApi.of(HttpMethod.GET, "/api/v1/oauth/google"),
			PublicApi.of(HttpMethod.GET, "/api/v1/oauth/google/callback"));

	/**
	 * 로그인이 필요하지만 브라우저가 이동하는 주소. 로그인하지 않았으면 JSON {@code 401} 대신 프론트엔드의 로그인 화면으로 보낸다
	 * ({@code docs/api/10-settings.md}의 {@code GET /api/v1/oauth/google/verify}).
	 */
	static final String GOOGLE_VERIFY = "/api/v1/oauth/google/verify";

	/**
	 * @param method 연 메서드. null이면 모든 메서드
	 * @param path 주소. 적은 주소만 연다. {@code *}는 주소의 한 마디(예: 재설정 링크 값)만 대신한다
	 */
	record PublicApi(HttpMethod method, String path) {

		static PublicApi anyMethod(String path) {
			return new PublicApi(null, path);
		}

		static PublicApi of(HttpMethod method, String path) {
			return new PublicApi(method, path);
		}

	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			CorsConfigurationSource corsConfigurationSource, SecurityErrorWriter errorWriter,
			FrontendProperties frontend) throws Exception {
		// 허용하지 않은 출처를 거절할 때도 응답 틀로 답하게 CORS 필터를 직접 만들어 넣는다.
		CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
		corsFilter.setCorsProcessor(new ApiCorsProcessor(errorWriter));
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(AbstractHttpConfigurer::disable)
				.addFilter(corsFilter)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				// 로그인하지 않은 요청을 세션에 저장해 두지 않는다. 저장하면 로그인하지 않은 사람에게도 세션이 생긴다.
				.requestCache(cache -> cache.disable())
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.authorizeHttpRequests(auth -> {
					for (PublicApi api : PUBLIC_API) {
						if (api.method() == null) {
							auth.requestMatchers(api.path()).permitAll();
						}
						else {
							auth.requestMatchers(api.method(), api.path()).permitAll();
						}
					}
					auth.requestMatchers("/api/**").authenticated()
							// Swagger, 헬스체크, 오류 응답(/error)
							.anyRequest().permitAll();
				})
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint((request, response, e) -> {
							if (HttpMethod.GET.matches(request.getMethod()) && GOOGLE_VERIFY.equals(request.getRequestURI())) {
								response.sendRedirect(frontend.frontendUrl() + "/login");
								return;
							}
							errorWriter.write(response, CommonErrorCode.UNAUTHENTICATED);
						})
						.accessDeniedHandler((request, response, e) ->
								errorWriter.write(response, CommonErrorCode.FORBIDDEN)));
		return http.build();
	}

	/** 로그인 정보(SecurityContext)를 세션에 둔다. 세션은 Spring Session이 Redis에 저장한다. */
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

}
