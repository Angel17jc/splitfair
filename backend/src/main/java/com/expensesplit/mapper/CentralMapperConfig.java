package com.expensesplit.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Configuracion comun de todos los mappers.
 *
 * <p><b>unmappedTargetPolicy = ERROR es el motivo de usar MapStruct aqui.</b>
 * No se trata de ahorrar lineas: con el mapeo manual, anadir un campo a un
 * DTO de respuesta y olvidar rellenarlo compila sin quejarse y devuelve null
 * en produccion. Con esta politica, ese olvido rompe la compilacion y el
 * fallo aparece antes de salir del editor.
 *
 * <p>El origen si puede tener campos sin usar: una entidad expone mas de lo
 * que la API debe devolver, y eso es deliberado.
 */
@MapperConfig(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CentralMapperConfig {
}
