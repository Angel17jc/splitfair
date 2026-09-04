# Imagen de produccion del frontend.
#
# Antes esta imagen arrancaba el servidor de desarrollo de Vite. Eso no es una
# imprecision: el dev server no esta pensado para exponerse —recarga en
# caliente, sin minificar, con sourcemaps del codigo completo— y ademas obliga
# a llevar dentro las dependencias de desarrollo y el codigo fuente. Lo que se
# publica ahora son ficheros estaticos servidos por nginx.

# ---------------------------------------------------------------------------
# 1. Compilacion
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
# ci y no install: instala exactamente el lockfile y falla si no concuerda con
# package.json, en vez de reconciliarlos en silencio.
RUN npm ci

COPY . .

# La API se sirve desde el mismo origen a traves de nginx (ver nginx.conf), asi
# que la ruta es relativa. Es lo que permite que esta misma imagen valga para
# cualquier dominio: Vite sustituye esta variable en tiempo de compilacion, y
# con una URL absoluta el dominio quedaria grabado en el paquete.
ENV VITE_API_BASE_URL=/api
RUN npm run build

# ---------------------------------------------------------------------------
# 2. Servicio
# ---------------------------------------------------------------------------
# nginx-unprivileged en vez de nginx: la imagen oficial arranca su proceso
# maestro como root. Aqui no hace falta para nada, y no necesitarlo es la forma
# mas barata de no tenerlo.
FROM nginxinc/nginx-unprivileged:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
