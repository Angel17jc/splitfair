package com.expensesplit.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;

/**
 * Comprueba que el codigo sea una moneda ISO 4217 real y que use dos
 * decimales.
 *
 * <p><b>Por que se exigen dos decimales:</b> todo el reparto de dinero de la
 * aplicacion trabaja en centimos con aritmetica entera, y el esquema almacena
 * NUMERIC(12,2). Monedas sin decimales como el yen japones, o con tres como
 * el dinar tunecino, se repartirian mal en silencio: un gasto de 1000 JPY
 * entre tres daria cuotas de 333.33 yenes, un importe que no existe.
 *
 * <p>Es preferible rechazar la moneda con un mensaje claro que aceptarla y
 * producir cuentas incorrectas. Admitirlas exigiria guardar los importes en
 * la unidad minima de cada moneda y generalizar MoneySplitter, que es un
 * cambio de calado y no una validacion.
 */
public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

    private static final int DECIMALES_SOPORTADOS = 2;

    @Override
    public boolean isValid(String codigo, ConstraintValidatorContext context) {
        if (codigo == null) {
            // La obligatoriedad se expresa con @NotNull, no aqui.
            return true;
        }

        Currency moneda;
        try {
            moneda = Currency.getInstance(codigo.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return mensaje(context, "'" + codigo + "' no es un codigo de moneda ISO 4217");
        }

        if (moneda.getDefaultFractionDigits() != DECIMALES_SOPORTADOS) {
            return mensaje(context, moneda.getCurrencyCode() + " usa "
                    + moneda.getDefaultFractionDigits() + " decimales y la aplicacion "
                    + "solo admite monedas de dos por ahora");
        }
        return true;
    }

    private boolean mensaje(ConstraintValidatorContext context, String texto) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(texto).addConstraintViolation();
        return false;
    }
}
