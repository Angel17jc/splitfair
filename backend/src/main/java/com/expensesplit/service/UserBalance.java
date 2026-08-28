package com.expensesplit.service;

import java.math.BigDecimal;

/**
 * Balance de un usuario dentro de un grupo, con los componentes que lo
 * originan.
 *
 * <p>Se identifica por id y no por la entidad {@code User}: usar entidades
 * JPA como clave de mapas depende de su identidad de instancia y de los
 * proxies perezosos de Hibernate, de modo que un mismo usuario puede acabar
 * ocupando dos entradas distintas y partir su balance en dos.
 *
 * <p>El desglose viaja junto al neto porque es lo que permite explicar la
 * cifra. "Debes 40" no dice nada por si solo; "pusiste 60 y te tocaban 100"
 * si, y es la diferencia entre que el usuario confie en el numero o abra una
 * discusion en el grupo.
 *
 * <p>Convenio de signo del neto:
 * <ul>
 *   <li>{@code amount > 0} le deben dinero (acreedor)</li>
 *   <li>{@code amount < 0} debe dinero (deudor)</li>
 *   <li>{@code amount == 0} esta a paz y salvo</li>
 * </ul>
 *
 * @param userId    usuario
 * @param userName  nombre, para no obligar a otra consulta
 * @param totalPaid lo que adelanto de su bolsillo
 * @param totalOwed lo que le correspondia asumir
 * @param amount    neto: {@code totalPaid - totalOwed}
 */
public record UserBalance(Long userId, String userName,
                          BigDecimal totalPaid, BigDecimal totalOwed,
                          BigDecimal amount) {

    /** Atajo para los tests y para el algoritmo, que solo miran el neto. */
    public static UserBalance of(Long userId, String userName, BigDecimal amount) {
        return new UserBalance(userId, userName, BigDecimal.ZERO, BigDecimal.ZERO, amount);
    }
}
