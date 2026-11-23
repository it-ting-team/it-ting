package com.itting.global.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 글자 수를 유니코드 문자(코드 포인트) 단위로 센다. MySQL의 {@code CHAR_LENGTH}, {@code VARCHAR(n)}와 같은 기준이다.
 * {@code @Size}는 UTF-16 단위로 세어 이모지 하나를 2자로 보므로, DB가 글자 수로 제한하는 칸에는 이것을 쓴다.
 * null은 검사하지 않는다(필수 여부는 따로 붙인다).
 */
@Documented
@Constraint(validatedBy = CodePointLength.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface CodePointLength {

	int min() default 0;

	int max() default Integer.MAX_VALUE;

	String message();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<CodePointLength, CharSequence> {

		private int min;

		private int max;

		@Override
		public void initialize(CodePointLength annotation) {
			this.min = annotation.min();
			this.max = annotation.max();
		}

		@Override
		public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
			if (value == null) {
				return true;
			}
			int length = Character.codePointCount(value, 0, value.length());
			return length >= min && length <= max;
		}

	}

}
