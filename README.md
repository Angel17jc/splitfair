# Gastos Compartidos (Splitwise Clone)

App full stack para gestionar gastos compartidos entre grupos de personas (roommates, viajes, etc.), con cálculo automático de balances y simplificación de deudas.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Data JPA, Spring Security + JWT, PostgreSQL, Maven, Lombok
- **Frontend:** React 18 + Vite, TypeScript, TailwindCSS, React Query, React Router
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

> Nota: si prefieres no instalar Maven globalmente, puedes generar el Maven Wrapper (`mvnw`) ejecutando `mvn -N wrapper:wrapper` dentro de `backend/` una vez tengas Maven instalado una sola vez, o simplemente usa `mvn` directamente como se indica abajo.

### 3. Node.js 20+ y npm

Descarga desde https://nodejs.org/ (elige la versión LTS) o usa un gestor de versiones como `nvm`:
```bash
nvm install 20
nvm use 20
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

Para continuar, puedes pedirle a tu agente que implemente estas piezas una por una, siguiendo la estructura y convenciones ya establecidas en el proyecto.
