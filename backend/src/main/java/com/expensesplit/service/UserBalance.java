package com.expensesplit.service;

import java.math.BigDecimal;

/**
 * Balance neto de un usuario dentro de un grupo.
 *
 * <p>Se identifica por id y no por la entidad {@code User}: usar entidades
 * JPA como clave de mapas depende de su identidad de instancia y de los
 * proxies perezosos de Hibernate, de modo que un mismo usuario puede acabar
 * ocupando dos entradas distintas y partir su balance en dos.
 *
 * <p>Convenio de signo:
 * <ul>
 *   <li>{@code amount > 0} le deben dinero (acreedor)</li>
 *   <li>{@code amount < 0} debe dinero (deudor)</li>
 *   <li>{@code amount == 0} esta a paz y salvo</li>
 * </ul>
 */
public record UserBalance(Long userId, String userName, BigDecimal amount) {
}
