#!/bin/sh
#
# Restauracion de una copia de seguridad.
#
# DESTRUYE los datos actuales: reemplaza el contenido de la base por el de la
# copia. Por eso exige nombrar el fichero explicitamente y confirmar.
#
#   docker compose -f docker-compose.prod.yml exec backup \
#       sh /scripts/restore.sh /backups/expense_split-20260904-030000.dump --si-estoy-seguro
#
# Antes de restaurar hay que parar el backend. Con la aplicacion en marcha,
# pg_restore intenta eliminar objetos que Hibernate esta usando y la
# restauracion queda a medias, que es peor situacion que la de partida.

set -eu

FICHERO="${1:-}"
CONFIRMACION="${2:-}"
BASE="${PGDATABASE:-expense_split}"

if [ -z "$FICHERO" ] || [ "$CONFIRMACION" != "--si-estoy-seguro" ]; then
    echo "Uso: sh restore.sh <fichero.dump> --si-estoy-seguro" >&2
    echo "" >&2
    echo "Copias disponibles:" >&2
    ls -lh "${DIRECTORIO_COPIAS:-/backups}"/*.dump 2>/dev/null >&2 || echo "  (ninguna)" >&2
    exit 1
fi

if [ ! -f "$FICHERO" ]; then
    echo "No existe el fichero: $FICHERO" >&2
    exit 1
fi

# Comprobar que el volcado es legible ANTES de tocar nada. pg_restore --list
# lee el indice del fichero: si esta truncado o corrupto, falla aqui y la base
# actual se queda intacta.
echo "[restauracion] comprobando la copia"
pg_restore --list "$FICHERO" > /dev/null
echo "[restauracion] la copia es legible"

echo "[restauracion] restaurando $FICHERO sobre $BASE"

# --clean --if-exists elimina los objetos antes de recrearlos, para que la
# restauracion no dependa de que la base este vacia.
#
# Sin --exit-on-error, pg_restore termina con exito habiendo saltado errores:
# la restauracion pareceria correcta con tablas faltantes.
pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error \
    --dbname="$BASE" "$FICHERO"

echo "[restauracion] completada"
