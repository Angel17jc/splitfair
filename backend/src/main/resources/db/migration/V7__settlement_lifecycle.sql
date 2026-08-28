-- =====================================================================
-- V7 - Ciclo de vida de las liquidaciones
--
-- La tabla existia desde V1 pero nunca se habia usado: las liquidaciones se
-- calculaban al vuelo y se tiraban. Para registrar pagos reales hacen falta
-- dos cosas mas.
--
-- 1. created_at, distinto de settled_at. Una liquidacion se registra cuando
--    alguien dice "te he pagado" y se confirma cuando el otro lo reconoce.
--    Entre ambos momentos puede pasar tiempo, y confundirlos impide saber
--    cuanto tarda la gente en confirmar.
--
-- 2. Coherencia entre estado y fecha de confirmacion. Sin la restriccion,
--    una liquidacion PENDING con settled_at relleno, o una CONFIRMED sin el,
--    son estados que el codigo nunca deberia producir pero que la base
--    aceptaria en silencio.
-- =====================================================================

ALTER TABLE settlements ADD COLUMN created_at TIMESTAMP(6);

UPDATE settlements SET created_at = COALESCE(settled_at, now()) WHERE created_at IS NULL;

ALTER TABLE settlements ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE settlements ADD CONSTRAINT ck_settlements_settled_consistent
    CHECK ((status = 'PENDING'   AND settled_at IS NULL)
        OR (status = 'CONFIRMED' AND settled_at IS NOT NULL));

-- El calculo de balances filtra por grupo y estado en cada lectura: solo las
-- confirmadas alteran las cuentas.
CREATE INDEX idx_settlements_group_status ON settlements (group_id, status);
