package io.github.badim1235.trackdrop.identity;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return PasswordPolicy.isValid(value);
	}
}
