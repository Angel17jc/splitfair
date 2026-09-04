#!/bin/sh
#
# Copia de seguridad de la base de datos.
#
# Se ejecuta dentro del contenedor "backup" de docker-compose.prod.yml, que
# usa la misma imagen de PostgreSQL que el servidor: asi pg_dump y el servidor
# van siempre a la misma version, que es la unica forma de que el volcado sea
# restaurable sin sorpresas.
#
# Uso manual (con el stack levantado):
#   docker compose -f docker-compose.prod.yml exec backup sh /scripts/backup.sh

set -eu

DESTINO="${DIRECTORIO_COPIAS:-/backups}"
RETENCION_DIAS="${RETENCION_DIAS:-14}"
BASE="${PGDATABASE:-expense_split}"

FECHA=$(date +%Y%m%d-%H%M%S)
FICHERO="$DESTINO/$BASE-$FECHA.dump"

mkdir -p "$DESTINO"

echo "[copia] iniciando volcado de $BASE hacia $FICHERO"

# --format=custom, no SQL plano: comprime, y sobre todo permite restaurar de
# forma selectiva una tabla concreta sin tener que reproducir el fichero
# entero.
#
# Se escribe con extension .parcial y se renombra al terminar. El renombrado
# es atomico, asi que un volcado interrumpido —el contenedor se para, el disco
# se llena— nunca queda con aspecto de copia valida. Sin esto, la copia mala
# solo se descubre el dia que hace falta usarla.
pg_dump --format=custom --no-owner --no-privileges --file="$FICHERO.parcial" "$BASE"
mv "$FICHERO.parcial" "$FICHERO"

TAMANO=$(du -h "$FICHERO" | cut -f1)
echo "[copia] completada: $FICHERO ($TAMANO)"

# La retencion se aplica despues de que la copia nueva este confirmada. Al
# reves, un fallo del volcado tras haber borrado las antiguas dejaria el
# sistema sin ninguna copia utilizable.
BORRADAS=$(find "$DESTINO" -name "$BASE-*.dump" -type f -mtime "+$RETENCION_DIAS" -print -delete | wc -l)
echo "[copia] copias eliminadas por antiguedad (> $RETENCION_DIAS dias): $BORRADAS"

# Un fichero .parcial abandonado significa que un volcado anterior se quedo a
# medias. Se avisa en vez de borrarlo en silencio: es la senal de que algo
# fallo y conviene mirarlo.
for restos in "$DESTINO"/*.parcial; do
    [ -e "$restos" ] || break
    echo "[copia] AVISO: hay un volcado incompleto de una ejecucion anterior: $restos"
done
