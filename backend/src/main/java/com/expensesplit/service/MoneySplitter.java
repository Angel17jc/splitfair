package com.expensesplit.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

    /**
     * Reparte {@code total} en proporcion a unos pesos, sin perder ni
     * inventar dinero.
     *
     * <p>Es la generalizacion de {@link #splitEqually}: con pesos todos
     * iguales produce el mismo resultado. Sirve para repartir por porcentajes
     * (pesos 50, 30, 20) o por partes (pesos 2, 1, 1).
     *
     * <p>El problema del centimo es el mismo y peor: repartir 100.00 entre
     * tres al 33.33% da 99.99, y al 33.34% da 100.02. Aqui se resuelve con el
     * mismo metodo del <b>mayor residuo</b>, pero calculado sobre la fraccion
     * exacta de cada peso:
     *
     * <ol>
     *   <li>a cada participante le corresponde
     *       {@code totalCentimos * peso / sumaPesos}, en general no entero;</li>
     *   <li>se le asigna la parte entera, y se anota el resto;</li>
     *   <li>los centimos que faltan para cuadrar el total van, de uno en uno,
     *       a quienes tienen mayor resto.</li>
     * </ol>
     *
     * <p>Todo el calculo va en BigInteger: los pesos se escalan a enteros por
     * su mayor numero de decimales, de modo que no hay division en coma
     * flotante ni redondeos intermedios en ningun punto.
     *
     * <p>Los empates de resto se rompen por posicion, asi que el reparto es
     * determinista: los mismos argumentos dan siempre el mismo resultado.
     *
     * @param total   importe a repartir, no negativo
     * @param weights pesos, no negativos y con al menos uno positivo
     * @return lista de importes con escala 2, en el orden de los pesos
     */
    public static List<BigDecimal> splitByWeights(BigDecimal total, List<BigDecimal> weights) {
        if (total == null) {
            throw new IllegalArgumentException("El importe a repartir no puede ser null");
        }
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("Debe haber al menos un peso");
        }
        if (total.signum() < 0) {
            throw new IllegalArgumentException("El importe a repartir no puede ser negativo: " + total);
        }
        if (weights.stream().anyMatch(w -> w == null || w.signum() < 0)) {
            throw new IllegalArgumentException("Los pesos no pueden ser nulos ni negativos");
        }

        // Escala comun para convertir los pesos en enteros exactos.
        int escala = weights.stream().mapToInt(BigDecimal::scale).max().orElse(0);
        List<BigInteger> pesos = weights.stream()
                .map(w -> w.setScale(escala).unscaledValue())
                .toList();

        BigInteger sumaPesos = pesos.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (sumaPesos.signum() <= 0) {
            throw new IllegalArgumentException("Al menos un peso debe ser mayor que cero");
        }

        BigInteger totalCents = BigInteger.valueOf(toCents(total));

        long[] base = new long[pesos.size()];
        BigInteger[] restos = new BigInteger[pesos.size()];
        BigInteger asignado = BigInteger.ZERO;

        for (int i = 0; i < pesos.size(); i++) {
            BigInteger[] division = totalCents.multiply(pesos.get(i)).divideAndRemainder(sumaPesos);
            base[i] = division[0].longValueExact();
            restos[i] = division[1];
            asignado = asignado.add(division[0]);
        }

        // Siempre menor que el numero de participantes: es cuantos reciben un
        // centimo mas que su parte entera.
        int sobrantes = totalCents.subtract(asignado).intValueExact();

        // Mayor resto primero; a igualdad de resto, menor posicion.
        Integer[] orden = new Integer[pesos.size()];
        for (int i = 0; i < orden.length; i++) {
            orden[i] = i;
        }
        Arrays.sort(orden, Comparator
                .comparing((Integer i) -> restos[i]).reversed()
                .thenComparingInt(i -> i));

        for (int i = 0; i < sobrantes; i++) {
            base[orden[i]]++;
        }

        List<BigDecimal> shares = new ArrayList<>(pesos.size());
        for (long cents : base) {
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
