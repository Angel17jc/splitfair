-- El valor original de cada parte del reparto.
--
-- Hasta ahora solo se guardaba `amount_owed`, el importe resultante. El dato
-- que lo produjo —el porcentaje, el numero de partes, la cantidad exacta— se
-- descartaba despues de repartir.
--
-- Eso hace imposible editar un gasto sin degradarlo. Con un reparto al 70/30,
-- cambiar el importe total exige los porcentajes para recalcularlo; con solo
-- los importes viejos, o se recalcula mal o hay que convertir el gasto a
-- "cantidades exactas" y perder la intencion. Es justo lo que se quiso evitar
-- al persistir `split_type`: que corregir una descripcion deshiciera el
-- reparto.
--
-- Nulo para las filas anteriores y para el reparto a partes iguales, que no
-- lleva valor: en EQUAL el importe se deduce del numero de participantes.
--
-- Cuatro decimales y no dos: aqui no siempre hay dinero. Un porcentaje como
-- 33.3333 o unas partes fraccionarias necesitan mas precision que un importe.
ALTER TABLE expense_splits ADD COLUMN split_value NUMERIC(12, 4);

COMMENT ON COLUMN expense_splits.split_value IS
    'Valor que el cliente indico para esta parte: porcentaje, numero de partes '
    'o importe exacto, segun el split_type del gasto. Nulo en EQUAL y en las '
    'filas creadas antes de esta migracion.';
