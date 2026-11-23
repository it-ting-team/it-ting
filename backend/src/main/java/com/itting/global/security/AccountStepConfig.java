package com.itting.global.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** {@link AccountStepInterceptor}를 {@code /api/**}에 건다. */
@Configuration
public class AccountStepConfig implements WebMvcConfigurer {

	private final AccountStepInterceptor accountStepInterceptor;

	public AccountStepConfig(AccountStepInterceptor accountStepInterceptor) {
		this.accountStepInterceptor = accountStepInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(accountStepInterceptor).addPathPatterns("/api/**");
	}

}
