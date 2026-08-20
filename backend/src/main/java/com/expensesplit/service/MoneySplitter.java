package com.expensesplit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reparte un importe entre N participantes sin perder ni inventar dinero.
 *
 * <p>El enfoque ingenuo — {@code total.divide(n, 2, HALF_UP)} para todos — no
 * cuadra, porque la cuota redondeada multiplicada por N rara vez devuelve el
 * total original:
 *
 * <pre>
 *   100.00 entre 3  ->  33.33 x3 =  99.99   (se pierde 0.01)
 *   100.00 entre 6  ->  16.67 x6 = 100.02   (se inventan 0.02)
 *   100.00 entre 7  ->  14.29 x7 = 100.03   (se inventan 0.03)
 * </pre>
 *
 * <p>En una aplicacion cuyo unico proposito es decir quien debe cuanto, ese
 * descuadre es inaceptable: se acumula gasto a gasto y los balances dejan de
 * sumar cero, con lo que el algoritmo de simplificacion de deudas nunca logra
 * dejar al grupo a paz y salvo.
 *
 * <p>La solucion es el metodo del <b>mayor residuo</b> (largest remainder):
 * se calcula la cuota base truncada en centimos y el residuo se reparte de a
 * un centimo entre los primeros participantes. Asi la suma de las partes es
 * <b>exactamente</b> el total, y la diferencia maxima entre dos participantes
 * es de un solo centimo.
 *
 * <p>Todo el calculo se hace en centimos con aritmetica entera (long): no hay
 * redondeos intermedios que puedan desviarse.
 */
public final class MoneySplitter {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneySplitter() {
        // clase de utilidad
    }

    /**
     * Divide {@code total} en {@code parts} importes que suman exactamente
     * {@code total}. Los primeros de la lista reciben el centimo sobrante
     * cuando la division no es exacta.
     *
     * <p>El reparto es determinista: para los mismos argumentos devuelve
     * siempre el mismo resultado, de modo que recalcular un gasto no cambia
     * quien asume el centimo extra.
     *
     * @param total  importe a repartir, positivo
     * @param parts  numero de participantes, mayor que cero
     * @return lista de {@code parts} importes con escala 2, en el mismo orden
     */
    public static List<BigDecimal> splitEqually(BigDecimal total, int parts) {
        if (total == null) {
            throw new IllegalArgumentException("El importe a repartir no puede ser null");
        }
        if (parts <= 0) {
            throw new IllegalArgumentException("Debe haber al menos un participante, recibido: " + parts);
        }
        if (total.signum() < 0) {
            throw new IllegalArgumentException("El importe a repartir no puede ser negativo: " + total);
        }

        long totalCents = toCents(total);
        long baseCents = totalCents / parts;
        // Siempre en [0, parts): es el numero de participantes que pagan un
        // centimo mas que el resto.
        int extraCents = (int) (totalCents % parts);

        List<BigDecimal> shares = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            long cents = baseCents + (i < extraCents ? 1 : 0);
            shares.add(fromCents(cents));
        }
        return shares;
    }

    private static long toCents(BigDecimal amount) {
        // setScale con HALF_UP normaliza entradas con mas de dos decimales
        // antes de convertir; longValueExact garantiza que no hay perdida
        // silenciosa si el importe excediera el rango de long.
        return amount.setScale(SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .longValueExact();
    }

    private static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, SCALE);
    }
}
