-- =====================================================================
-- V6 - Categoria del gasto como valor cerrado
--
-- La columna era texto libre. Con texto libre, "comida", "Comida" y "comidas"
-- son tres categorias distintas, y cualquier filtro o grafica por categoria
-- acaba siendo una lista de errores tipograficos en lugar de un agregado.
--
-- Los valores existentes se normalizan a mayusculas y los que no encajan en
-- el catalogo pasan a OTROS. Se pierde el matiz de esos textos, pero eran
-- datos de desarrollo y la alternativa (mantener dos representaciones) es
-- peor: la aplicacion tendria que decidir en cada lectura si lo que hay es
-- una categoria o una nota.
-- =====================================================================

UPDATE expenses SET category = UPPER(TRIM(category)) WHERE category IS NOT NULL;

UPDATE expenses
SET category = 'OTROS'
WHERE category IS NULL
   OR category NOT IN ('COMIDA', 'TRANSPORTE', 'ALOJAMIENTO', 'OCIO',
                       'SERVICIOS', 'COMPRAS', 'SALUD', 'OTROS');

ALTER TABLE expenses ALTER COLUMN category SET NOT NULL;

ALTER TABLE expenses ADD CONSTRAINT ck_expenses_category
    CHECK (category IN ('COMIDA', 'TRANSPORTE', 'ALOJAMIENTO', 'OCIO',
                        'SERVICIOS', 'COMPRAS', 'SALUD', 'OTROS'));

-- Soporta el filtro por categoria dentro de un grupo, que es como se
-- consulta siempre: nunca se piden todas las comidas de la base.
CREATE INDEX idx_expenses_group_category ON expenses (group_id, category);

-- El filtro por quien pago se apoya en idx_expenses_paid_by, ya existente
-- desde V1.
