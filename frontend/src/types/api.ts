/**
 * Tipos espejo de los DTO del backend.
 *
 * Se escriben a mano y no se generan desde el esquema OpenAPI a proposito: el
 * fichero es corto, se lee de un vistazo y sirve de contrato explicito. Si el
 * backend cambia una forma, la diferencia se ve aqui en una revision en vez de
 * aparecer como un `undefined` en pantalla.
 *
 * Contrastado endpoint a endpoint contra la API en marcha.
 *
 * ## Los importes llegan como numero, no como cadena
 *
 * El backend serializa `BigDecimal` como numero JSON (`33.33`, `0.00`), asi
 * que en JavaScript son `number`, es decir coma flotante de doble precision.
 * Dos consecuencias que hay que respetar:
 *
 * 1. **El backend es la unica autoridad sobre el dinero.** Aqui se muestran
 *    importes, no se calculan. Sumar cuotas en el cliente para "comprobar"
 *    un total reintroduce justo el error de redondeo que el reparto por mayor
 *    residuo existe para evitar.
 * 2. **Formatear siempre con dos decimales.** `0.00` llega como `0` y
 *    `33.30` como `33.3`; pintarlo tal cual da importes que parecen rotos.
 *
 * ## Hay dos formas de fecha distintas
 *
 * - `expiresAt` de las invitaciones es un instante UTC: `2026-09-04T04:32:47.900208Z`.
 * - `createdAt` de grupos y liquidaciones es una fecha-hora **sin zona**:
 *   `2026-08-31T22:23:40.814808`. `new Date()` la interpreta como hora local.
 * - `expenseDate` es solo el dia: `2026-08-28`.
 */

// --- envoltorios comunes ---------------------------------------------------

/** Respuesta paginada. `size` admite como maximo 100. */
export interface Paged<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

/**
 * Cuerpo de error de la API.
 *
 * `fieldErrors` solo viene en los 400 de validacion y mapea nombre de campo a
 * mensaje, que es justo lo que necesita react-hook-form para pintar el error
 * bajo su input.
 */
export interface ErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  traceId?: string
  fieldErrors?: Record<string, string>
}

// --- autenticacion ---------------------------------------------------------

/**
 * Credenciales de una sesion recien abierta o renovada.
 *
 * No incluye el refresh token, y no es un olvido: viaja en una cookie
 * HttpOnly acotada a `/api/auth` que ningun script puede leer. El cliente no
 * lo ve, no lo guarda y no lo envia.
 */
export interface Auth {
  accessToken: string
  /** Vida del access token en **segundos** (900). */
  expiresIn: number
  userId: number
  name: string
  email: string
  /** Solo si el registro traia un token de invitacion. */
  joinedGroupId?: number
}

export interface RegisterInput {
  name: string
  email: string
  password: string
  invitationToken?: string
}

export interface LoginInput {
  email: string
  password: string
}

// --- usuario ---------------------------------------------------------------

export interface User {
  id: number
  name: string
  email: string
  createdAt: string
}

export interface ChangePasswordInput {
  currentPassword: string
  newPassword: string
}

// --- grupos ----------------------------------------------------------------

export type GroupRole = 'ADMIN' | 'MEMBER'

export interface GroupMember {
  userId: number
  name: string
  email: string
  role: GroupRole
}

/** Fila del listado de grupos: trae ya el saldo propio, sin pedirlo aparte. */
export interface GroupSummary {
  id: number
  name: string
  description: string | null
  currency: string
  createdAt: string
  role: GroupRole
  memberCount: number
  myBalance: number
}

export interface Group {
  id: number
  name: string
  description: string | null
  currency: string
  createdByName: string
  createdAt: string
  members: GroupMember[]
}

export interface CreateGroupInput {
  name: string
  description?: string
  /** Se fija al crear y no cambia. Solo monedas de 2 decimales. */
  currency?: string
}

export interface UpdateGroupInput {
  name: string
  description?: string
}

// --- invitaciones ----------------------------------------------------------

export interface Invitation {
  id: number
  url: string
  token: string
  email?: string
  expiresAt: string
}

/**
 * Vista previa publica de un link de invitacion. Se sirve sin autenticar, asi
 * que expone lo minimo para decidir si aceptar.
 *
 * `valid: false` significa caducada o ya usada, y llega con **200, no 404**:
 * el link existio, solo que ya no sirve.
 */
export interface InvitationPreview {
  groupName: string
  invitedByName: string
  expiresAt: string
  valid: boolean
}

// --- gastos ----------------------------------------------------------------

export const CATEGORIAS = [
  'COMIDA',
  'TRANSPORTE',
  'ALOJAMIENTO',
  'OCIO',
  'SERVICIOS',
  'COMPRAS',
  'SALUD',
  'OTROS',
] as const

export type Category = (typeof CATEGORIAS)[number]

export const TIPOS_DE_REPARTO = ['EQUAL', 'EXACT', 'PERCENTAGE', 'SHARES'] as const

export type SplitType = (typeof TIPOS_DE_REPARTO)[number]

export interface ExpenseSplit {
  userId: number
  userName: string
  amountOwed: number
}

export interface Expense {
  id: number
  description: string
  amount: number
  category: Category
  splitType: SplitType
  expenseDate: string
  paidByName: string
  splits: ExpenseSplit[]
}

/** Una parte del reparto. El significado de `value` depende del `splitType`. */
export interface SplitInput {
  userId: number
  /** EXACT: importe · PERCENTAGE: porcentaje (suman 100) · SHARES: partes. */
  value: number
}

export interface ExpenseInput {
  description: string
  amount: number
  /** ISO `YYYY-MM-DD`. */
  expenseDate: string
  /** Por defecto OTROS. */
  category?: Category
  /** Por defecto EQUAL. */
  splitType?: SplitType
  /** Obligatorio salvo en EQUAL. */
  splits?: SplitInput[]
  /** Atajo solo para EQUAL: entre quienes se reparte. */
  splitBetweenUserIds?: number[]
}

export interface ExpenseFilters {
  category?: Category
  /** ISO `YYYY-MM-DD`. */
  from?: string
  to?: string
  paidBy?: number
}

// --- balances y liquidaciones ----------------------------------------------

/**
 * Saldo de un miembro, con el desglose que lo explica.
 *
 * `netBalance` = (totalPaid − totalOwed) + (settlementsPaid − settlementsReceived).
 * Las liquidaciones van aparte de lo adelantado en gastos porque adelantar
 * dinero y saldar una deuda son cosas distintas.
 */
export interface Balance {
  userId: number
  userName: string
  totalPaid: number
  totalOwed: number
  settlementsPaid: number
  settlementsReceived: number
  netBalance: number
}

/** Los `netBalance` de un grupo suman siempre cero. */
export interface GroupBalances {
  currency: string
  /** Suma de lo adelantado en gastos. No incluye liquidaciones: saldar no es gastar. */
  totalSpent: number
  balances: Balance[]
}

/** Pago sugerido por el algoritmo para saldar el grupo en el minimo de transacciones. */
export interface SettlementSuggestion {
  fromUserId: number
  fromUserName: string
  toUserId: number
  toUserName: string
  amount: number
}

export type SettlementStatus = 'PENDING' | 'CONFIRMED'

/**
 * Pago real entre dos miembros.
 *
 * Solo altera los balances cuando esta `CONFIRMED`, y solo puede confirmarlo
 * quien cobra: una pendiente es la palabra de una sola parte.
 */
export interface Settlement {
  id: number
  paidByUserId: number
  paidByName: string
  paidToUserId: number
  paidToName: string
  amount: number
  currency: string
  status: SettlementStatus
  createdAt: string
  settledAt?: string
}

export interface CreateSettlementInput {
  paidTo: number
  amount: number
}

// --- paginacion ------------------------------------------------------------

export interface PageParams {
  page?: number
  size?: number
}
