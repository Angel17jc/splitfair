-- =====================================================================
-- V5 - Tipo de reparto del gasto
--
-- Se guarda para poder mostrarlo y, sobre todo, para reeditar un gasto sin
-- perder la intencion original. Sin este campo, un gasto repartido al 70/30
-- volveria a partes iguales en cuanto alguien corrigiera su descripcion,
-- porque el servidor no tendria forma de saber como se habia repartido.
--
-- Los gastos anteriores a esta migracion se repartieron a partes iguales,
-- que es lo unico que existia hasta ahora.
-- =====================================================================

ALTER TABLE expenses ADD COLUMN split_type VARCHAR(20);

UPDATE expenses SET split_type = 'EQUAL' WHERE split_type IS NULL;

ALTER TABLE expenses ALTER COLUMN split_type SET NOT NULL;

ALTER TABLE expenses ADD CONSTRAINT ck_expenses_split_type
    CHECK (split_type IN ('EQUAL', 'EXACT', 'PERCENTAGE', 'SHARES'));
