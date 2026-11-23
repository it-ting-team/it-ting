package com.itting.user.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 닉네임 규칙: 한글(완성된 글자) · 영어 · 숫자로 1 ~ 12자. 가운데 공백, 특수문자, 이모지, 낱자({@code ㅋㅋ})는 쓸 수 없다.
 * 앞뒤 공백은 요청 DTO가 지운 뒤 검사한다. null도 어긴 것으로 본다.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@NotNull
@Pattern(regexp = "[가-힣A-Za-z0-9]{1,12}")
@ReportAsSingleViolation
public @interface Nickname {

	String message() default "한글, 영어, 숫자로 1 ~ 12자여야 합니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
