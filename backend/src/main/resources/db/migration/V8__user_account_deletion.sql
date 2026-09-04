-- Baja de cuenta.
--
-- No se borra la fila. Un usuario aparece en gastos, en repartos y en
-- liquidaciones confirmadas: borrarlo dejaria apuntes contables sin dueno y,
-- lo que es peor, los balances del grupo dejarian de sumar cero. El dinero se
-- evaporaria del informe sin que nadie hubiera pagado nada.
--
-- Lo que se hace es anonimizar: se sustituyen los datos personales —nombre y
-- correo— y se marca la fecha de baja. El historico contable queda intacto y
-- la persona deja de ser identificable, que es lo que exige el derecho de
-- supresion sin romper la contabilidad del grupo.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP(6);

COMMENT ON COLUMN users.deleted_at IS
    'Fecha de baja. Si no es nula, la fila esta anonimizada: name y email ya '
    'no contienen datos personales y el hash de contrasena es irrecuperable.';

-- Indice parcial: solo indexa las filas dadas de baja, que son una minoria.
-- Un indice completo ocuparia tanto como la tabla para responder a una
-- consulta que casi siempre filtra por "no dado de baja".
CREATE INDEX idx_users_deleted_at ON users (deleted_at) WHERE deleted_at IS NOT NULL;
