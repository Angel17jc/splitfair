# Plan de construcción — SplitFair

App de gastos compartidos entre grupos (roommates, viajes). Este documento es la
hoja de ruta viva del proyecto: se actualiza al cerrar cada fase.

- **Repo:** https://github.com/Angel17jc/splitfair
- **Última revisión:** 2026-08-20
- **Estado:** Fases 0-4 **completadas** (25 commits, 347 tests en verde) · siguiente: Fase 5 (frontend)

---

## 1. Decisiones tomadas

| Tema | Decisión | Consecuencia |
|---|---|---|
| Destino | **Producto real con usuarios** | Entran fases de seguridad endurecida, refresh tokens, rate limiting, observabilidad y backups. No basta con "funciona en local". |
| Alta de miembros | **Invitación por link con token** | Tabla `invitations`, flujo de registro-con-invitación y expiración de tokens. |
| Moneda | **Única, fijada por grupo** | Campo `currency` en `groups`. Sin tabla de tasas ni API de cambio. |
| Frontend | **React 18** (se mantiene) | Router 7 y Vite 8, ya sin vulnerabilidades. React 19 queda fuera de alcance. |

### Stack confirmado

**Backend** — Java 21, Spring Boot 3.3.4, Spring Data JPA + Hibernate, Spring Security + JWT (jjwt 0.12.6),
PostgreSQL 16, Maven (con wrapper), Lombok, MapStruct 1.6.2, Bean Validation, springdoc-openapi 2.6.0.

**Frontend** — React 18.3, Vite 8.2, TypeScript 5.9, Axios, TailwindCSS 3.4, React Router 7.18,
TanStack Query 5, React Hook Form + Zod.

**Infra** — Docker + Docker Compose (postgres, backend, frontend).

**Testing** — JUnit 5 + Mockito + Testcontainers (backend), Vitest + Testing Library (frontend).

---

## 2. Auditoría del estado actual

El backend está **más avanzado de lo que aparenta**: entidades, repositorios, auth con JWT,
CRUD de grupos, alta de gastos, cálculo de balances y el algoritmo greedy ya están escritos
y compilan, con 5 tests unitarios en verde.

Pero hay defectos que deben corregirse **antes** de construir encima, porque toda la lógica
posterior se apoya en ellos.

### 2.1 Defectos críticos

**A. El reparto de gastos descuadra (verificado, no es teoría).**

`ExpenseService` divide con `amount.divide(n, 2, HALF_UP)` y asigna esa cuota a cada
participante. La suma de las cuotas no da el total. Salida real de una prueba ejecutada
sobre esa misma fórmula:

```
100.00 entre 3  -> cuota 33.33  x3 =  99.99  (descuadre -0.01)
100.00 entre 6  -> cuota 16.67  x6 = 100.02  (descuadre +0.02)
100.00 entre 7  -> cuota 14.29  x7 = 100.03  (descuadre +0.03)
  0.10 entre 7  -> cuota  0.01  x7 =   0.07  (descuadre -0.03)
```

No solo se pierde dinero: en varios casos **se inventa**. En una app cuyo único propósito
es decir quién debe cuánto, es el fallo más grave del proyecto. Se corrige con reparto por
**mayor residuo** (largest remainder): cuota base truncada y el sobrante repartido de a
un céntimo, de forma determinista.

**B. Cualquier usuario autenticado puede leer y modificar cualquier grupo (IDOR).**

Ningún método comprueba que el usuario pertenezca al grupo:

- `GroupService.getGroup(id)` — lee cualquier grupo por ID
- `GroupService.addMember(groupId, userId)` — mete a cualquiera en cualquier grupo
- `ExpenseService.getGroupExpenses / getGroupBalances / getSuggestedSettlements`
- `ExpenseService.createExpense` — carga gastos a grupos ajenos

`SecurityConfig` exige `authenticated()`, lo que verifica *quién eres*, nunca *a qué tienes
derecho*. Con un token válido y un bucle sobre IDs se lee la contabilidad completa de todos
los grupos de la base. Es la vulnerabilidad más seria y bloquea cualquier despliegue público.

**C. Riesgo de `LazyInitializationException` en las lecturas.**

`getGroupExpenses` y `calculateNetBalances` recorren `expense.getSplits()` y `split.getUser()`,
ambos `LAZY`, sin `@Transactional`. Funciona en tests con la sesión abierta y revienta al
servir peticiones reales. De paso genera **N+1 queries**: una por gasto, más una por split.

**D. `Map<User, BigDecimal>` como clave de balances.**

`User` no sobreescribe `equals`/`hashCode`, así que el mapa agrupa por identidad de
instancia. Hoy sobrevive porque Hibernate devuelve la misma instancia dentro de una sesión,
pero con proxies lazy o varias sesiones un mismo usuario puede ocupar **dos entradas** y
partir su balance en dos. Se sustituye por `Map<Long, BigDecimal>` con el ID como clave.

**E. Los miembros sin gastos desaparecen de los balances.**

`calculateNetBalances` solo recorre gastos. Un miembro que no pagó ni participó en ninguno
no aparece en `GET /balances`, cuando debería salir con balance `0.00`.

**F. El manejador de errores filtra detalles internos.**

`GlobalExceptionHandler.handleGeneric` devuelve `"Error interno: " + ex.getMessage()` al
cliente. Eso expone mensajes de SQL, rutas y detalles de infraestructura. Para un producto
con usuarios reales hay que registrar el detalle en el log y devolver un mensaje genérico
con un identificador de correlación.

**G. Sin migraciones: `ddl-auto: update`.**

Hibernate improvisa el esquema al arrancar. Nunca borra ni corrige columnas, así que el
esquema real diverge del código sin aviso, y no hay forma de reproducir ni revertir un
cambio. Incompatible con un producto en producción. Se migra a **Flyway**.

### 2.2 Funcionalidad faltante frente a la especificación

| Endpoint / pieza | Estado |
|---|---|
| `GET /api/groups` (mis grupos) | **No existe** |
| `DELETE /api/groups/{id}/members/{userId}` | No existe |
| `PUT` / `DELETE /api/expenses/{id}` | No existen |
| `POST /api/settlements/{id}/confirm` | No existe |
| `SettlementService` / `SettlementController` | No existen |
| `UserController` | No existe |
| `OpenApiConfig` | No existe (springdoc está sin configurar) |
| Mappers MapStruct | Dependencia declarada, carpeta vacía; el mapeo es manual |
| Persistencia de liquidaciones | Las sugerencias se calculan al vuelo y se tiran |

### 2.3 Deuda menor

- `show-sql: true` y `logging DEBUG` en la configuración base, no en un perfil `dev`.
- `devtools.restart` activo en la configuración base.
- CORS con `http://localhost:5173` fijo en el código.
- Sin índices en las claves foráneas (`group_id`, `user_id`, `expense_id`).
- `JwtAuthFilter` no distingue "token ausente" de "token inválido": ambos acaban en un 403 opaco.
- Sin `equals`/`hashCode` en las entidades JPA.
- `ExpenseSplitRepository.findByExpenseGroupId` declarado y nunca usado.
- Frontend: las tres páginas son marcadores `TODO`; no hay contexto de auth, rutas protegidas ni uso real de React Query.

---

## 3. Fases

Cada fase cierra con la suite en verde y la rama `main` desplegable. Los mensajes de commit
siguen [Conventional Commits](https://www.conventionalcommits.org/).

---

### Fase 0 — Estabilización del backend ✅ COMPLETADA

> **Por qué va primero:** los balances son el corazón del producto. Construir invitaciones y
> UI sobre un reparto que descuadra y un acceso sin autorización significa reescribir esas
> capas después. Esta fase no añade funcionalidad; deja el terreno firme.

**Commits (5)**

1. `build(backend): migrar el esquema a Flyway y desactivar ddl-auto`
   - `V1__initial_schema.sql` con las 6 tablas, claves foráneas, `UNIQUE(group_id, user_id)` e índices en las FK.
   - `ddl-auto: validate` para que la app falle al arrancar si el esquema no coincide.
   - Perfiles `dev` / `prod`: `show-sql` y logging DEBUG solo en `dev`.

2. `fix(expenses): repartir los centimos sin descuadre (largest remainder)`
   - Cuota base con `RoundingMode.DOWN`; el residuo se reparte de a un céntimo entre los primeros participantes por orden de ID.
   - Test parametrizado: para todo monto y todo `n`, la suma de los splits **es exactamente** el total.
   - Casos borde: montos que no dividen (0.10 entre 7), un solo participante, montos grandes.

3. `feat(security): autorizacion por pertenencia al grupo`
   - `GroupAccessService` central: `requireMember(groupId, email)` y `requireAdmin(...)`.
   - Aplicado en todos los métodos de `GroupService` y `ExpenseService`.
   - `ForbiddenException` → HTTP 403 en el manejador global.
   - Tests: un miembro accede; un extraño recibe 403; un `MEMBER` no puede ejecutar acciones de `ADMIN`.

4. `fix(backend): transaccionalidad y consultas N+1 en lecturas`
   - `@Transactional(readOnly = true)` en las lecturas de servicio.
   - `@EntityGraph` o `JOIN FETCH` para traer `splits` y sus usuarios en una consulta.
   - `Map<User, BigDecimal>` → `Map<Long, BigDecimal>`; `equals`/`hashCode` por ID en las entidades.
   - Balances incluyen a **todos** los miembros del grupo, con `0.00` si no participaron.

5. `fix(backend): no filtrar detalles internos en las respuestas de error`
   - `handleGeneric` registra el stack trace con un `traceId` y devuelve un mensaje genérico con ese ID.
   - Formato de error unificado y documentado.

**Criterio de cierre:** ✅ los splits cuadran al céntimo para cualquier combinación
(verificado sobre 500.000 repartos); un usuario ajeno recibe 403 en los seis endpoints
(verificado por mutación); `./mvnw test` en verde con 76 tests.

**Hallazgos durante la ejecución**, no previstos en el plan:

- El cálculo de balances pasó de >30 consultas con 15 gastos a **5 constantes**. El test
  lo mide con las estadísticas de Hibernate en vez de afirmarlo.
- `open-in-view` estaba activo y enmascaraba las cargas perezosas accidentales. Desactivado.
- El `EPSILON` del algoritmo greedy era `0.01`, con lo que un saldo de exactamente un
  céntimo se descartaba como saldado. Ahora es `0.005`.
- Los tests de integración no estaban aislados: al ir por HTTP no hay transacción que
  revierta los datos. La limpieza se centralizó en la clase base.
- Entorno: Docker 29 exige `MinAPIVersion` 1.44 y `docker-java` negocia una anterior,
  lo que Testcontainers reporta como *"Could not find a valid Docker environment"*.
  Fijado en el `pom.xml`. Documentado en el README junto al conflicto de puerto 5432
  con un PostgreSQL nativo.

---

### Fase 1 — Autenticación de nivel producto ✅ COMPLETADA

> Hoy hay un único token de 24 h sin forma de revocarlo: si se filtra, el atacante entra un
> día entero. Para usuarios reales eso no alcanza.

**Commits (4)**

1. `feat(auth): refresh tokens con rotacion y revocacion`
   - Access token corto (15 min) + refresh token largo (30 días) en tabla `refresh_tokens`.
   - Rotación en cada uso y detección de reutilización (si llega un refresh ya usado, se revoca toda la familia).
   - `POST /api/auth/refresh` y `POST /api/auth/logout`.

2. `fix(security): distinguir 401 de 403 y responder en JSON`
   - `AuthenticationEntryPoint` → 401 con cuerpo JSON.
   - `AccessDeniedHandler` → 403 con cuerpo JSON.
   - `JwtAuthFilter` diferencia token ausente, malformado y expirado.

3. `feat(security): rate limiting en los endpoints de autenticacion`
   - Bucket4j sobre `/api/auth/**` por IP y por email.
   - Freno al descubrimiento de contraseñas por fuerza bruta.
   - Respuesta 429 con cabecera `Retry-After`.

4. `feat(users): endpoint de perfil y tests de integracion de auth`
   - `UserController`: `GET /api/users/me`, `PATCH /api/users/me`.
   - Tests de integración con Testcontainers sobre PostgreSQL real: registro → login → refresh → logout → token revocado.

**Criterio de cierre:** ✅ un refresh robado y reutilizado invalida la familia completa;
al agotar el cupo de login se devuelve 429 con `Retry-After`.

**Hallazgos durante la ejecución:**

- **Dos bugs encadenados en la detección de reutilización.** La revocación de la familia
  se ejecutaba y acto seguido se lanzaba una excepción; Spring revertía la transacción al
  propagarse, **deshaciendo la revocación**. El control de seguridad quedaba anulado por
  el propio mecanismo transaccional, en silencio. Y `noRollbackFor` en el servicio interno
  no bastó, porque `AuthService.refresh` también era transaccional y, siendo la externa,
  era la suya la que decidía el rollback.
- El límite de peticiones se aplica **por IP y por email a la vez**: cada dimensión cubre
  un ataque que la otra deja pasar (botnet contra una cuenta vs. *password spraying*).
- El cambio de contraseña revoca todas las sesiones: si no, un intruso conservaría un
  refresh token válido 30 días y el cambio no serviría de nada.
- Se apretaron las aserciones `is4xxClientError` que dejó la Fase 0: no distinguían
  precisamente el fallo 401/403 que esta fase corrige.

---

### Fase 2 — Grupos, miembros e invitaciones ✅ COMPLETADA

**Commits (5)**

1. `feat(groups): listar los grupos del usuario autenticado`
   - `GET /api/groups` — el endpoint que falta de la especificación.
   - Paginación con `Pageable` y resumen por grupo (nº de miembros, balance propio).

2. `feat(groups): gestion de roles ADMIN y MEMBER`
   - Solo `ADMIN` invita, expulsa y edita el grupo; solo `ADMIN` puede promover a otro.
   - Regla: un grupo nunca se queda sin `ADMIN`.

3. `feat(invitations): generar link de invitacion con token`
   - Tabla `invitations` (`token`, `group_id`, `invited_by`, `email`, `expires_at`, `accepted_at`).
   - `POST /api/groups/{id}/invitations` devuelve el link; token opaco de un solo uso con caducidad de 7 días.

4. `feat(invitations): aceptar invitacion con y sin cuenta previa`
   - `GET /api/invitations/{token}` — vista previa pública (nombre del grupo, quién invita).
   - `POST /api/invitations/{token}/accept` — usuario autenticado se une.
   - Registro con invitación: crear cuenta y unirse en un solo paso.
   - Casos: token caducado, ya usado, usuario ya miembro.

5. `feat(groups): salir del grupo y expulsar miembros`
   - `DELETE /api/groups/{id}/members/{userId}`.
   - **Regla de negocio:** no se puede salir ni expulsar a alguien con balance distinto de cero. Si no, la deuda desaparece del sistema.

**Criterio de cierre:** ✅ un usuario nuevo llega por link, se registra y queda dentro del grupo
en una sola petición; un miembro con deuda pendiente no puede salir.

**Hallazgos durante la ejecución:**

- **La regla "no salir con deuda" se demostró, no se supuso.** Un test se salta la validación
  borrando la pertenencia por SQL y comprueba que la suma de balances pasa de `0.00` a `30.00`:
  treinta euros desaparecidos. Es la prueba empírica de por qué la regla existe.
- **El registro con invitación va en una sola petición por atomicidad**, no por comodidad: con
  dos llamadas, un fallo entre ambas deja al usuario registrado y fuera del grupo.
- `GET /api/groups` resuelve el balance de todos los grupos de la página con **dos agregaciones**:
  5 consultas para 12 grupos, medidas con las estadísticas de Hibernate.
- Se extrajo `SecureTokens`: la generación y el hasheo estaban a punto de duplicarse entre
  refresh tokens e invitaciones.
- **Dos tests frágiles de fases anteriores, corregidos.** Uno alteraba el *último* carácter de la
  firma JWT, que en base64url puede no cambiar los bytes decodificados (43 caracteres = 258 bits
  para 256 útiles). Otro fijaba un token de 1 segundo a nivel de clase, volviendo sensibles al
  reloj a todos los tests de esa clase.

---

### Fase 3 — Gastos ✅ COMPLETADA

**Commits (5)**

1. `feat(groups): moneda por grupo`
   - Campo `currency` (ISO 4217) en `groups`, fijado al crear e inmutable después.
   - Todos los montos del grupo se interpretan en esa moneda.

2. `feat(expenses): editar y eliminar gastos`
   - `PUT /api/expenses/{id}` y `DELETE /api/expenses/{id}`.
   - Solo quien pagó o un `ADMIN`; los splits se recalculan de forma atómica.

3. `feat(expenses): splits personalizados`
   - Estrategias `EQUAL`, `EXACT` (montos), `PERCENTAGE`, `SHARES` (partes).
   - Validación: los montos exactos suman el total; los porcentajes suman 100.
   - Patrón *strategy* para no llenar el servicio de condicionales.

4. `feat(expenses): categorias, filtros y paginacion`
   - Enum `ExpenseCategory` (comida, transporte, alojamiento, ocio, servicios, otros).
   - Filtros por categoría, rango de fechas y pagador; resultados paginados.

5. `test(expenses): tests de integracion del modulo de gastos`
   - Alta con cada estrategia de split, edición con recálculo, borrado y su efecto en los balances.

**Criterio de cierre:** ✅ las cuatro estrategias cuadran al céntimo (verificado sobre ~76.000
repartos); editar un gasto deja los balances consistentes.

**Hallazgos durante la ejecución:**

- **El problema del céntimo reaparece con los porcentajes.** 33,33 % de 100 tres veces da 99,99.
  Se generalizó `MoneySplitter` a un reparto por pesos con mayor residuo, en `BigInteger`, sin
  coma flotante ni redondeos intermedios.
- **Trampa de paginación:** paginar una consulta que además trae los splits hace que Hibernate
  cargue todo y pagine **en memoria** (aviso `HHH90003004`). Se resolvió en dos fases —la base
  pagina identificadores, luego se hidrata la página—. Medido: 5 consultas para 25 gastos.
- **Trampa de Lombok:** `@Builder.Default` no se aplica cuando el builder recibe un `null`
  explícito, solo cuando el campo se omite. Un gasto sin categoría llegaba con `null` a una
  columna `NOT NULL`.
- **Se rechazan las monedas sin dos decimales** (JPY, KRW, CLP): todo el reparto trabaja en
  céntimos, y aceptarlas daría cuentas incorrectas en silencio.

---

### Fase 4 — Balances y liquidaciones ✅ COMPLETADA

> El núcleo del proyecto y lo más defendible en una entrevista técnica.

**Commits (5)**

1. `refactor(balances): calculo robusto e independiente de la sesion JPA`
   - Consulta agregada en SQL en vez de recorrer entidades en memoria.
   - Incluye a todos los miembros; devuelve además el desglose "te deben / debes".

2. `feat(settlements): persistir liquidaciones y confirmarlas`
   - `POST /api/groups/{id}/settlements` registra un pago real como `PENDING`.
   - `POST /api/settlements/{id}/confirm` — solo el receptor confirma; pasa a `CONFIRMED`.
   - `SettlementService` + `SettlementController`.

3. `feat(balances): descontar las liquidaciones confirmadas`
   - `balance = pagado - adeudado + liquidaciones_recibidas - liquidaciones_pagadas`.
   - Sin esto, una deuda saldada reaparece eternamente en las sugerencias.

4. `test(settlements): propiedades invariantes del algoritmo greedy`
   - Tests basados en propiedades con balances aleatorios:
     - la suma de las transacciones sugeridas es cero;
     - nunca se generan más de `n-1` transacciones;
     - aplicar las sugerencias deja a todos en cero;
     - ningún monto sugerido es negativo.
   - Casos límite: 2, 3, 5, 20 personas; grupo saldado; una persona pagó todo; céntimos sueltos.

5. `refactor(backend): mappers MapStruct y documentacion OpenAPI`
   - Sustituir los `toResponse` manuales por mappers generados.
   - `OpenApiConfig` con el esquema `bearerAuth`, para probar la API autenticada desde Swagger UI.

**Criterio de cierre:** ✅ los tests de propiedades pasan sobre ~1.550 configuraciones
aleatorias; confirmar una liquidación elimina esa deuda de las sugerencias.

**Hallazgos durante la ejecución:**

- **Swagger UI respondía 401.** `/swagger-ui.html` es la URL que se teclea y no casa con el
  patrón `/swagger-ui/**` de `SecurityConfig`: la documentación estaba generada pero era
  inaccesible. Corregido y cubierto por un test.
- **Solo las liquidaciones confirmadas cuentan.** Si una pendiente alterara los balances,
  bastaría declarar un pago inexistente para borrar una deuda.
- **Una liquidación confirmada no se borra**: es un hecho contable. Para corregirla se
  registra el pago inverso, como en cualquier libro de cuentas.
- El coste de los balances subió de 5 a 8 consultas al añadir las agregaciones de
  liquidaciones; una era evitable (el guardián de acceso ya carga el grupo). Quedan **7,
  constantes**.
- MapStruct con `unmappedTargetPolicy = ERROR` **sí rompe el build** al olvidar un campo,
  pero solo en compilación completa: la incremental no siempre regenera el mapper.

---

### Fase 5 — Frontend: base y autenticación

**Commits (4)**

1. `feat(frontend): capa de API tipada y manejo de errores`
   - Tipos TypeScript espejo de los DTO del backend.
   - Interceptor de Axios que renueva el access token con el refresh de forma transparente y encola las peticiones en vuelo.

2. `feat(auth): contexto de sesion y rutas protegidas`
   - `AuthProvider` + hook `useAuth`.
   - `<ProtectedRoute>` con redirección a login preservando el destino.
   - Refresh token en cookie `httpOnly`; access token en memoria (**no** en `localStorage`, que queda expuesto a XSS — hoy `client.ts` lo guarda ahí).

3. `feat(auth): pantallas de login y registro`
   - React Hook Form + Zod; esquemas de validación compartidos.
   - Estados de carga y error, mensajes del backend mapeados a campos.

4. `feat(ui): layout, componentes base y estados vacios`
   - `Button`, `Input`, `Card`, `Modal`, `Spinner`, `EmptyState`, `ErrorBoundary`.
   - Layout con navegación y menú de usuario.

**Criterio de cierre:** sesión que sobrevive al recargar; el access token caducado se renueva
sin que el usuario lo note.

---

### Fase 6 — Frontend: grupos y gastos

**Commits (4)**

1. `feat(groups): listado y detalle de grupos`
   - React Query con claves bien diseñadas e invalidación selectiva.
   - Skeletons de carga y estado vacío con llamada a la acción.

2. `feat(groups): creacion de grupo e invitaciones`
   - Formulario de alta con selección de moneda.
   - Generar link de invitación y copiarlo al portapapeles; pantalla pública de aceptación.

3. `feat(expenses): listado y alta de gastos`
   - Lista con filtros por categoría y fecha, y scroll infinito.
   - Formulario de alta con split igual y selección de participantes.

4. `feat(expenses): splits personalizados en la interfaz`
   - Selector de estrategia con validación en vivo del cuadre.
   - Actualización optimista con reversión si el servidor rechaza.

**Criterio de cierre:** flujo completo end-to-end — crear grupo, invitar, registrar gasto, ver
el reparto correcto.

---

### Fase 7 — Frontend: dashboard de balances

**Commits (4)**

1. `feat(dashboard): balances visuales por grupo`
   - Quién debe a quién, con código de color acreedor/deudor y resumen personal.

2. `feat(dashboard): liquidaciones sugeridas y confirmacion`
   - Lista de transacciones mínimas con explicación de por qué son las mínimas.
   - Registrar pago y confirmarlo con actualización optimista.

3. `feat(dashboard): analitica de gastos`
   - Gasto por categoría y evolución temporal.
   - Paleta accesible, legible en tema claro y oscuro.

4. `test(frontend): tests de componentes y flujos criticos`
   - Vitest + Testing Library sobre formularios, cálculo mostrado y rutas protegidas.

**Criterio de cierre:** el dashboard refleja los balances reales y confirmar una liquidación
los actualiza al instante.

---

### Fase 8 — Producción

**Commits (5)**

1. `ci(github): pipeline de integracion continua`
   - Backend: `./mvnw verify` con Testcontainers.
   - Frontend: `npm ci`, `lint`, `build`, `test`.
   - `npm audit` y `dependency-check` como puerta de calidad. Bloqueo de merge si algo falla.

2. `build(docker): imagenes de produccion`
   - Backend multi-stage con JRE Alpine y usuario no-root.
   - Frontend compilado y servido por nginx (hoy el Dockerfile arranca el **dev server**, que no debe exponerse).
   - `docker-compose.prod.yml` separado del de desarrollo.

3. `feat(ops): observabilidad`
   - Spring Boot Actuator: `health`, `readiness`, `liveness`, métricas.
   - Logs estructurados en JSON con `traceId` por petición.
   - Healthchecks reales en Compose.

4. `feat(ops): backups y politica de datos personales`
   - Script de `pg_dump` programado y procedimiento de restauración **probado**.
   - Borrado de cuenta con anonimización que preserva la integridad contable histórica.

5. `docs: guia de despliegue y documentacion de la API`
   - Despliegue a Railway/Render/Fly.io con variables de entorno documentadas.
   - README con capturas, decisiones de arquitectura y explicación del algoritmo.

**Criterio de cierre:** CI bloqueando merges rotos; app accesible en una URL pública;
restauración de backup verificada.

---

## 4. Mejoras propuestas sobre la especificación original

Añadidos que no estaban en el plan inicial y que considero justificados:

| Mejora | Fase | Motivo |
|---|---|---|
| Flyway en vez de `ddl-auto` | 0 | Sin esquema versionado no hay producto en producción. |
| Reparto por mayor residuo | 0 | El reparto actual pierde e inventa dinero. |
| Autorización por pertenencia | 0 | Sin ella, la contabilidad de todos los grupos es pública. |
| Refresh tokens con rotación | 1 | Un token de 24 h sin revocación es inaceptable con usuarios reales. |
| Rate limiting | 1 | Login sin freno = fuerza bruta gratis. |
| Regla "no salir con deuda" | 2 | Sin ella el dinero se evapora del sistema. |
| Tests de propiedades | 4 | Un greedy se valida mejor por invariantes que por casos sueltos. |
| Access token fuera de `localStorage` | 5 | `localStorage` es legible por cualquier XSS. |
| nginx para el frontend en producción | 8 | El dev server de Vite no es un servidor de producción. |
| Anonimización en el borrado de cuenta | 8 | Permite borrar al usuario sin romper la contabilidad del grupo. |

### Descartado deliberadamente

- **Multi-moneda** — decidido: moneda única por grupo.
- **React 19 / Router 8 / Tailwind 4** — decidido: React 18.
- **Microservicios** — el monolito modular es la elección correcta a esta escala.
- **WebSockets para tiempo real** — React Query con revalidación cubre el caso; volver si aparece la necesidad.

---

## 5. Convenciones

**Commits** — Conventional Commits: `tipo(alcance): descripción en imperativo`.
Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`.

**Ramas** — `main` siempre desplegable. Una rama por fase (`fase-0-estabilizacion`),
integrada por PR con CI en verde.

**Definición de "terminado"** — código + tests + documentación actualizada + CI en verde
+ sin secretos versionados.

**Secretos** — todo por variables de entorno. `.env` nunca se sube; `.env.example`
documenta cada variable. Ningún valor por defecto de un secreto vive en el repositorio.
