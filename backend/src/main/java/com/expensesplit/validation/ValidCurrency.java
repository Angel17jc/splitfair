package com.expensesplit.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Valida que el valor sea un codigo de moneda ISO 4217 admitido por la
 * aplicacion. Un valor nulo se considera valido: la obligatoriedad se expresa
 * por separado con @NotNull.
 */
@Documented
@Constraint(validatedBy = CurrencyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrency {

    String message() default "Moneda no valida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
