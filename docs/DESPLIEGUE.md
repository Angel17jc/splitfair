# Guía de despliegue

Cómo poner SplitFair en producción, qué decisiones lleva dentro la
configuración y qué hacer cuando algo va mal.

Para levantar el proyecto **en desarrollo**, mira el [README](../README.md).
Este documento es solo producción.

---

## Lo que se despliega

Tres contenedores y un cuarto que solo hace copias:

```
                    ┌─────────────────────────────┐
   internet ──443──▶│  nginx  (imagen "frontend") │
                    │  · sirve la SPA compilada   │
                    │  · reenvía /api al backend  │
                    └──────────────┬──────────────┘
                                   │ red interna, sin puertos publicados
                    ┌──────────────▼──────────────┐
                    │  backend  (Spring Boot)     │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐        ┌──────────┐
                    │  postgres                   │◀───────│  backup  │
                    └─────────────────────────────┘        └──────────┘
```

**Solo nginx publica un puerto.** Ni la base de datos ni el backend son
alcanzables desde fuera de la red de Compose. No es una precaución
decorativa: de ello depende que el backend pueda fiarse de la cabecera
`X-Forwarded-For` para contar los intentos de login (ver *Rate limiting*, más
abajo).

**La API va en el mismo origen que la aplicación.** nginx reenvía `/api` al
backend, así que el navegador ve un único origen. Eso elimina CORS, permite
que la cookie del refresh token funcione con `SameSite=Strict`, y evita que
la URL de la API quede grabada en el build: la misma imagen sirve en
cualquier dominio.

---

## Requisitos

- Docker y Docker Compose en el servidor.
- Un dominio apuntando a la máquina.
- **HTTPS**, y no es opcional: ver la sección siguiente.

---

## HTTPS no es opcional

En el perfil `prod`, la cookie del refresh token se marca `Secure`. Por HTTP
plano **el navegador no la envía**, y el síntoma es desconcertante: todo
parece funcionar, el usuario entra sin problema, y exactamente quince minutos
después —cuando caduca el access token— la sesión se cae sin ningún error en
los logs.

Dos formas de resolverlo, y la primera es la recomendada:

1. **Terminar TLS por delante**, con el proxy del proveedor (Railway, Render,
   Fly.io) o con un Caddy/Traefik en la misma máquina. nginx sigue escuchando
   en claro dentro de la red y recibe `X-Forwarded-Proto: https`.
2. Terminar TLS en el propio nginx, montando los certificados y añadiendo un
   `server` en el 443. Hay que mantener la renovación por tu cuenta.

Lo que **no** hay que hacer es poner `REFRESH_COOKIE_SECURE=false` para que
funcione sin certificado: eso hace que una credencial de treinta días viaje en
claro por la red.

---

## Puesta en marcha

```bash
git clone https://github.com/Angel17jc/splitfair.git
cd splitfair

cp .env.example .env
$EDITOR .env          # ver la tabla de abajo

docker compose -f docker-compose.prod.yml up -d --build
```

La primera construcción tarda varios minutos: compila el backend con Maven y
el frontend con Vite dentro de los propios contenedores.

Para comprobar que ha ido bien:

```bash
docker compose -f docker-compose.prod.yml ps
```

Los tres servicios deben aparecer como `healthy`. Si el backend se queda en
`starting` más de un minuto, mira sus logs: casi siempre es la base de datos
o el `JWT_SECRET`.

### Variables de entorno

Sin valor por defecto, el arranque falla a propósito. Es preferible a que un
despliegue quede protegido por una contraseña de ejemplo.

| Variable | Obligatoria | Qué es |
|---|---|---|
| `DB_PASSWORD` | **sí** | Contraseña de PostgreSQL. |
| `JWT_SECRET` | **sí** | Clave de firma de los access token. Mínimo 32 bytes; se valida al arrancar. Genérala con `openssl rand -base64 48`. |
| `DB_NAME`, `DB_USER` | no | Por defecto `expense_split` / `postgres`. |
| `HTTP_PORT` | no | Puerto publicado por nginx. Por defecto 80. |
| `JWT_ACCESS_EXPIRATION_MS` | no | 900000 (15 min). |
| `JWT_REFRESH_EXPIRATION_DAYS` | no | 30. |
| `RETENCION_COPIAS_DIAS` | no | Días que se conservan las copias. Por defecto 14. |
| `FRONTEND_BASE_URL` | **en la práctica sí** | URL pública. Se usa para construir los links de invitación: si apunta a `localhost`, los links que reciban los invitados no funcionarán. |

> **Cambiar `JWT_SECRET` cierra todas las sesiones abiertas.** Los access
> token en circulación dejan de validar. No es un problema —el cliente renueva
> con su refresh token— pero conviene saberlo antes de hacerlo un lunes a las
> nueve.

---

## Actualizar a una versión nueva

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

Flyway aplica las migraciones pendientes al arrancar el backend. Está
verificado sobre una base con datos, no solo sobre una vacía.

Hay unos segundos de corte mientras el backend se reinicia. Las peticiones en
curso terminan antes de cerrar, porque el perfil `prod` activa el apagado
ordenado.

**Antes de actualizar, lanza una copia manual** si la migración toca datos:

```bash
docker compose -f docker-compose.prod.yml exec backup sh /scripts/backup.sh
```

---

## Copias de seguridad

El contenedor `backup` hace un volcado **cada día a las 3:00**, y otro al
arrancar. Las copias viven en el volumen `copias` y se conservan
`RETENCION_COPIAS_DIAS` días.

```bash
# Ver las copias que hay
docker compose -f docker-compose.prod.yml exec backup ls -lh /backups

# Lanzar una a mano
docker compose -f docker-compose.prod.yml exec backup sh /scripts/backup.sh
```

### ⚠️ Sacar las copias de la máquina

Tal como está, **las copias viven en el mismo disco que los datos**, así que
protegen de un borrado accidental o una migración mal hecha, pero **no** de
perder el servidor. Esa es la otra mitad del trabajo y hay que hacerla:

```bash
# Desde otra máquina, por ejemplo en un cron diario a las 5:00
docker compose -f docker-compose.prod.yml exec -T backup \
    tar -c -C /backups . | ssh copias@otra-maquina 'cat > splitfair-$(date +%F).tar'
```

O montar `copias` sobre un almacenamiento remoto y sincronizarlo con `rclone`
hacia S3, Backblaze o equivalente.

### Restaurar

**Destruye los datos actuales.** El procedimiento está probado de extremo a
extremo; hay que seguirlo entero, empezando por parar el backend:

```bash
# 1. Parar la aplicación. Con ella en marcha, pg_restore intenta eliminar
#    objetos que Hibernate está usando y la restauración queda a medias.
docker compose -f docker-compose.prod.yml stop backend

# 2. Ver qué copias hay y elegir una
docker compose -f docker-compose.prod.yml exec backup ls -lh /backups

# 3. Restaurar
docker compose -f docker-compose.prod.yml exec backup \
    sh /scripts/restore.sh /backups/expense_split-AAAAMMDD-HHMMSS.dump --si-estoy-seguro

# 4. Arrancar
docker compose -f docker-compose.prod.yml start backend
```

El script comprueba que la copia sea legible **antes** de tocar nada: si está
truncada o corrupta, falla ahí y la base actual se queda intacta.

---

## Observabilidad

### Sondas de estado

| Ruta | Para qué |
|---|---|
| `/actuator/health` | Estado general. Es la única pública, y sin detalles. |
| `/actuator/health/liveness` | ¿Sigue vivo el proceso? Si no, reinícialo. |
| `/actuator/health/readiness` | ¿Puede atender tráfico? Responde 503 mientras migra el esquema. |

Ninguna es alcanzable desde internet: nginx solo reenvía `/api`. Se consultan
desde dentro:

```bash
docker compose -f docker-compose.prod.yml exec backend \
    wget -qO- http://127.0.0.1:8080/actuator/health/readiness
```

Las métricas (`/actuator/metrics`, `/actuator/prometheus`) exigen
autenticación además de no estar publicadas. Para que un Prometheus las
recoja, añádelo a la red de Compose.

### Logs

En producción salen en **JSON** por la salida estándar, un objeto por línea:

```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

Cada línea lleva un campo `traceId`, el mismo que aparece en el mensaje de
error que ve el usuario cuando algo falla de forma inesperada. Es lo que
convierte "me ha dado un error" en una traza concreta:

```bash
docker compose -f docker-compose.prod.yml logs backend | grep 'a1b2c3d4'
```

---

## Rate limiting detrás del proxy

El backend limita los intentos de login **por IP y por email**. Detrás de
nginx, todas las peticiones llegarían con la dirección del contenedor del
proxy, y los 5 intentos cada 15 minutos pasarían a ser 5 para toda la
aplicación: una denegación de servicio contra los usuarios legítimos.

Lo resuelven dos piezas que van juntas:

1. nginx envía `X-Forwarded-For` con la dirección real del cliente.
2. El perfil `prod` activa `server.forward-headers-strategy=FRAMEWORK`, que
   hace que Spring la interprete y devuelva la dirección real.

**Si pones otro proxy por delante, tiene que propagar esa cabecera.** Y el
puerto del backend no debe publicarse nunca: la cabecera la envía quien hace
la petición, así que si alguien puede alcanzar el backend directamente, le
basta rotarla para saltarse el límite.

El límite está en memoria. Con varias réplicas se multiplica por el número de
réplicas; para escalar hay que mover los contadores a Redis.

---

## Documentación de la API

El contrato completo está en OpenAPI, generado desde el propio código.

**En producción está desactivado a propósito**: publicar el catálogo entero de
endpoints, parámetros y formas de error es reconocimiento gratis para quien
busque por dónde entrar. Para consultarlo, levanta el proyecto en desarrollo:

```
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs        (JSON)
```

El resumen del contrato —rutas, cuerpos y códigos de estado— está en el
[README](../README.md#la-api-de-un-vistazo).

---

## Cuando algo va mal

| Síntoma | Causa casi siempre |
|---|---|
| La sesión se cae a los 15 minutos exactos | Sin HTTPS: la cookie `Secure` no viaja. Ver arriba. |
| Todos los POST devuelven 403 | El proxy de delante no envía el `Host` con el puerto. Spring reconstruye un origen distinto del de la página y lo trata como CORS ajeno. |
| 502 en `/api` | El backend no está sano. `docker compose ps` y luego sus logs. |
| El backend no arranca y habla de Flyway | Una migración ya aplicada cambió de contenido. No se editan las aplicadas: se añade una nueva. |
| El backend no arranca y habla de `validate` | Las entidades y las tablas divergen. Falta una migración. |
| Un usuario dice que no puede entrar y no se equivoca de contraseña | Habrá agotado el límite de intentos. Se libera solo en 15 minutos; la respuesta trae `Retry-After`. |
| Los links de invitación no funcionan | `FRONTEND_BASE_URL` apunta a `localhost`. |
