package com.itting.auth.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 규칙({@code docs/spec/00-common.md} 9). 회원가입 · 비밀번호 재설정 · 비밀번호 변경에 같이 쓴다.
 * 어긋난 규칙마다 {@code fields}에 한 줄씩 나간다. null은 검사하지 않는다(필수 여부는 따로 붙인다).
 */
@Documented
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Size(min = 8, max = 20, message = "8자 이상 20자 이하여야 합니다.")
@Pattern(regexp = "[A-Za-z0-9!@#$%]*", message = "영문, 숫자, 특수기호 !@#$%만 쓸 수 있습니다.")
@Pattern(regexp = ".*[A-Za-z].*", message = "영문을 1개 이상 넣어야 합니다.")
@Pattern(regexp = ".*[0-9].*", message = "숫자를 1개 이상 넣어야 합니다.")
@Pattern(regexp = ".*[!@#$%].*", message = "특수기호를 1개 이상 넣어야 합니다.")
public @interface ValidPassword {

	String message() default "비밀번호 규칙에 맞지 않습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
