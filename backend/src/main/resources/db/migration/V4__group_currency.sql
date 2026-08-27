-- =====================================================================
-- V4 - Moneda del grupo
--
-- Cada grupo fija su moneda al crearse y no cambia despues. Cambiarla
-- reinterpretaria todos los gastos ya registrados: 50.00 dejaria de ser
-- cincuenta euros para pasar a ser cincuenta dolares, sin que ningun importe
-- se hubiera tocado. Convertir el historico exigiria conocer la tasa de cada
-- fecha, que es justamente lo que este proyecto decidio no hacer.
--
-- Se anade en tres pasos porque la tabla ya tiene filas: primero la columna
-- nullable, luego el relleno, y por ultimo la restriccion. Hacerlo de una vez
-- con NOT NULL fallaria sobre cualquier grupo existente.
-- =====================================================================

ALTER TABLE groups ADD COLUMN currency VARCHAR(3);

-- Los grupos creados antes de esta migracion se interpretan en la moneda por
-- defecto de la aplicacion.
UPDATE groups SET currency = 'USD' WHERE currency IS NULL;

ALTER TABLE groups ALTER COLUMN currency SET NOT NULL;

-- Codigo ISO 4217: tres letras mayusculas. La validacion de que el codigo
-- existe de verdad vive en la aplicacion, que dispone del catalogo completo;
-- aqui se acota la forma para que ningun camino alternativo (una migracion
-- futura, una carga manual) meta basura en la columna.
ALTER TABLE groups ADD CONSTRAINT ck_groups_currency CHECK (currency ~ '^[A-Z]{3}$');
