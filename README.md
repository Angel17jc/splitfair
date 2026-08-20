# Gastos Compartidos (Splitwise Clone)

App full stack para gestionar gastos compartidos entre grupos de personas (roommates, viajes, etc.), con cálculo automático de balances y simplificación de deudas.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Data JPA, Spring Security + JWT, PostgreSQL, Maven, Lombok
- **Frontend:** React 18 + Vite 8, TypeScript, TailwindCSS, React Query, React Router 7
- **Infra:** Docker + Docker Compose

---

## Requisitos previos

Ya tienes Docker y VS Code. Necesitas instalar además:

### 1. JDK 21

**Windows:** descarga el instalador desde https://adoptium.net/ (elige Temurin 21 LTS) y sigue el wizard.

**Mac (con Homebrew):**
```bash
brew install openjdk@21
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

Verifica la instalación:
```bash
java -version
```
Deberías ver algo como `openjdk version "21..."`.

### 2. Maven

**Mac:**
```bash
brew install maven
```

**Linux:**
```bash
sudo apt install maven
```

**Windows:** descarga desde https://maven.apache.org/download.cgi y agrega la carpeta `bin` al PATH.

Verifica:
```bash
mvn -version
```

> Nota: el repo ya incluye el **Maven Wrapper**, asi que no necesitas instalar Maven globalmente.
> Desde `backend/` usa `./mvnw` (Linux/Mac) o `mvnw.cmd` (Windows) en lugar de `mvn`.

### 3. Node.js 22 LTS o superior, y npm

Vite 8 requiere Node `^20.19.0 || >=22.12.0`. Recomendado: Node 22 LTS.

Descarga desde https://nodejs.org/ (elige la versión LTS) o usa un gestor de versiones como `nvm`:
```bash
nvm install 22
nvm use 22
```

Verifica:
```bash
node -v
npm -v
```

### 4. Extensiones recomendadas de VS Code

Abre VS Code, ve a la pestaña de extensiones (Ctrl+Shift+X) e instala:
- **Extension Pack for Java** (Microsoft)
- **Spring Boot Extension Pack** (VMware/Microsoft)
- **ES7+ React/Redux/React-Native snippets**
- **Tailwind CSS IntelliSense**
- **Docker** (Microsoft)

---

## Cómo levantar el proyecto

### Opción A: Todo con Docker Compose (recomendado para empezar)

Desde la raíz del proyecto:
```bash
docker compose up --build
```

Esto levanta:
- PostgreSQL en `localhost:5432`
- Backend (Spring Boot) en `localhost:8080`
- Frontend (React) en `localhost:5173`

Para detenerlo:
```bash
docker compose down
```

Para borrar también los datos de la base de datos:
```bash
docker compose down -v
```

### Opción B: Backend y frontend por separado (útil mientras desarrollas)

**1. Levanta solo la base de datos con Docker:**
```bash
docker compose up postgres -d
```

**2. Backend (en una terminal, dentro de `backend/`):**
```bash
cd backend
mvn spring-boot:run
```
El backend queda corriendo en `http://localhost:8080`.

**3. Frontend (en otra terminal, dentro de `frontend/`):**
```bash
cd frontend
npm install
npm run dev
```
El frontend queda corriendo en `http://localhost:5173`.

---

## Verificar que todo funciona

1. Con el backend corriendo, abre `http://localhost:8080/swagger-ui.html` para ver la documentación interactiva de la API (Swagger).
2. Prueba el endpoint de registro desde Swagger o con curl:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana Test","email":"ana@test.com","password":"password123"}'
```

Deberías recibir un JSON con un `token` JWT.

3. Abre `http://localhost:5173` para ver el frontend (aún con páginas placeholder, listas para desarrollarse).

---

## Base de datos y migraciones

El esquema lo gobierna **Flyway**, no Hibernate. Los scripts viven en
`backend/src/main/resources/db/migration/` y se aplican solos al arrancar.

`spring.jpa.hibernate.ddl-auto` está en `validate`: la aplicación se niega a
arrancar si las entidades y las tablas han divergido. Eso convierte un error
silencioso en un fallo inmediato y visible.

**Para cambiar el esquema** se añade una migración nueva (`V2__...sql`). Nunca
se edita una ya aplicada: Flyway guarda su hash y aborta si cambia.

### Conflicto de puerto en el 5432

Si tienes un PostgreSQL instalado de forma nativa (en Windows es habitual, como
servicio `postgresql-x64-NN`), ocupará el 5432 y tus conexiones irán a él en vez
de al contenedor, con un desconcertante `password authentication failed`.

Comprueba quién escucha:

```bash
netstat -ano | grep ":5432"          # Linux / Git Bash
Get-NetTCPConnection -LocalPort 5432 # PowerShell
```

Solución: en tu `.env`, usa otro puerto para el contenedor.

```env
DB_PORT=5434
```

Solo cambia el puerto del lado del host. Dentro de Docker Compose el backend
sigue hablando con `postgres:5432` por la red interna, así que no hay que tocar
nada más.

## Autenticación

Dos credenciales con responsabilidades distintas:

| | Vida | Naturaleza | Revocable |
|---|---|---|---|
| **Access token** | 15 min | JWT, sin estado | No |
| **Refresh token** | 30 días | Valor opaco, con estado en BD | Sí |

El access token no se puede revocar, y por eso vive poco: su validez es el
tiempo máximo que sobrevive una credencial robada. La capacidad de cortar una
sesión vive en el refresh token.

**Rotación.** Cada refresco invalida el token presentado y emite uno nuevo
dentro de la misma *familia*. Si alguna vez se presenta un token ya rotado,
significa que existe una copia en circulación: se revoca la familia entera.
No se puede distinguir a la víctima del atacante, así que se corta el acceso
a ambos, y la víctima detecta el problema al verse obligada a entrar de nuevo
(RFC 9700).

En la base solo se guarda el SHA-256 del token, nunca el token.

### Rate limiting

`/api/auth/login` y `/api/auth/register` están limitados **por IP y por email
a la vez**, porque cada dimensión cubre un ataque que la otra deja pasar: solo
por IP, una botnet prueba miles de contraseñas contra una cuenta; solo por
email, una sola máquina prueba una contraseña habitual contra miles de cuentas
(*password spraying*).

Configurable con `RATE_LIMIT_LOGIN_ATTEMPTS`, `RATE_LIMIT_LOGIN_WINDOW_MINUTES`
y sus equivalentes de registro.

> **Limitación conocida:** el estado vive en memoria, así que con varias
> instancias cada una aplica su propio límite y el efectivo se multiplica por
> el número de réplicas. Para escalar horizontalmente hay que mover los buckets
> a Redis (`bucket4j-redis`), sin cambiar el resto del diseño.

> **Detrás de un proxy inverso** hay que configurar
> `server.forward-headers-strategy=FRAMEWORK`. La IP se toma de
> `getRemoteAddr()` y no de `X-Forwarded-For` leída a mano: esa cabecera la
> envía el cliente y puede falsificarse, con lo que bastaría rotarla para
> saltarse el límite.

## Tests

```bash
cd backend
./mvnw test
```

Los tests de integración levantan un **PostgreSQL 16 real con Testcontainers**,
no H2. H2 acepta SQL que PostgreSQL rechaza y no implementa igual `NUMERIC` ni
las palabras reservadas: dejaría pasar migraciones que fallan en producción.

Requiere Docker en marcha. El `pom.xml` fija `docker.api.version` porque los
daemon recientes (Docker 25+, `MinAPIVersion` 1.44) rechazan con HTTP 400 la
versión de API que `docker-java` negocia por defecto, y Testcontainers reporta
entonces un engañoso *"Could not find a valid Docker environment"*. Si tu Docker
es más antiguo, ajústalo:

```bash
./mvnw test -Ddocker.api.version=1.43
```

## Estructura del proyecto

```
splitwise-clone/
├── backend/
│   ├── src/main/java/com/expensesplit/
│   │   ├── config/         Spring Security, CORS
│   │   ├── controller/     Endpoints REST
│   │   ├── service/        Lógica de negocio (incluye el algoritmo de deudas)
│   │   ├── repository/     Interfaces JPA
│   │   ├── model/          Entidades (User, Group, Expense, etc.)
│   │   ├── dto/             Objetos de transferencia (request/response)
│   │   ├── security/        JWT, filtros de autenticación
│   │   └── exception/       Manejo global de errores
│   └── src/test/           Tests unitarios (incluye el test del algoritmo de deudas)
├── frontend/
│   └── src/
│       ├── api/            Llamadas a la API con Axios
│       ├── pages/           Páginas (Login, Register, Dashboard)
│       ├── routes/           Configuración de rutas
│       └── ...
└── docker-compose.yml
```

## Ya está implementado (punto de partida)

- ✅ Modelo de datos completo (User, Group, GroupMember, Expense, ExpenseSplit, Settlement)
- ✅ Registro y login con JWT
- ✅ Crear grupos y agregar miembros
- ✅ Registrar gastos con división en partes iguales
- ✅ Cálculo de balances netos por usuario
- ✅ **Algoritmo de simplificación de deudas** (con tests unitarios) — la parte más interesante del proyecto
- ✅ Estructura base del frontend en React con routing

## Lo que falta por construir

- ⬜ Formularios reales en el frontend (Login, Register, crear grupo, agregar gasto) conectados a la API
- ⬜ Dashboard visual con balances y gráficos
- ⬜ Splits personalizados (no equitativos) por gasto
- ⬜ Confirmar settlements (marcar deudas como pagadas)
- ⬜ Categorías de gastos y filtros
- ⬜ Tests de integración del backend

