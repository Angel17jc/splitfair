import { useEffect, useId, useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import Button from '../../components/Button'
import Input from '../../components/Input'
import Modal from '../../components/Modal'
import Select from '../../components/Select'
import { aplicarErrorDeApi } from '../../utils/formularios'
import { hoyISO, parsearImporte } from '../../utils/dinero'
import {
  CATEGORIAS,
  TIPOS_DE_REPARTO,
  type Expense,
  type GroupMember,
  type SplitInput,
} from '../../types/api'
import { ETIQUETA_DE_CATEGORIA } from './categorias'
import { aCentimos, comprobarCuadre, MODOS_DE_REPARTO } from './reparto'
import SplitEditor from './SplitEditor'
import { useActualizarGasto, useCrearGasto } from './hooks'

const esquema = z
  .object({
    description: z
      .string()
      .trim()
      .min(1, 'Describe el gasto')
      .max(255, 'La descripcion es demasiado larga'),
    amount: z
      .string()
      .min(1, 'Indica el importe')
      .refine((texto) => parsearImporte(texto) !== null, {
        message: 'Importe no valido. Usa como maximo dos decimales, por ejemplo 12,50',
      }),
    expenseDate: z.string().min(1, 'Indica la fecha'),
    category: z.enum(CATEGORIAS),
    splitType: z.enum(TIPOS_DE_REPARTO),
    participantes: z
      .array(z.number())
      .min(1, 'Elige al menos una persona entre las que repartir'),
    valores: z.record(z.string()),
  })
  /**
   * El cuadre depende de tres campos a la vez —modo, importe y valores— asi
   * que no puede validarse campo a campo. Se comprueba aqui, sobre el objeto
   * completo, y con la misma funcion que alimenta el indicador en vivo: una
   * sola definicion de "cuadra", en vez de dos que pueden discrepar.
   */
  .superRefine((datos, ctx) => {
    if (datos.splitType === 'EQUAL') return

    const total = aCentimos(datos.amount)
    if (total === null) return // ya hay un error en el importe

    const valores = datos.participantes.map((id) => datos.valores[String(id)] ?? '')
    const cuadre = comprobarCuadre(datos.splitType, valores, total)

    if (!cuadre.valido) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['valores'], message: cuadre.mensaje })
    }
  })

type Datos = z.infer<typeof esquema>

const CAMPOS = ['description', 'amount', 'expenseDate', 'category'] as const

interface Props {
  abierto: boolean
  onCerrar: () => void
  groupId: number
  miembros: GroupMember[]
  /**
   * El gasto que se esta editando, o nada para crear uno nuevo.
   *
   * El padre debe montar este componente con una `key` distinta por gasto:
   * los valores iniciales del formulario se calculan al montar, y sin la key
   * reabrirlo con otro gasto mostraria los datos del anterior.
   */
  gasto?: Expense
}

export default function ExpenseModal({ abierto, onCerrar, groupId, miembros, gasto }: Props) {
  const crear = useCrearGasto(groupId)
  const actualizar = useActualizarGasto(groupId)
  const editando = gasto !== undefined

  /**
   * Identificador propio del formulario.
   *
   * El boton de envio vive en el pie del modal, fuera del `<form>`, y se
   * enlaza con el atributo `form`. Con un id fijo eso se rompe en cuanto hay
   * **dos instancias montadas** —una para crear y otra para editar—: los dos
   * formularios comparten id, el navegador resuelve el atributo contra el
   * primero del documento, y el boton de "Guardar cambios" acaba enviando el
   * formulario vacio del modal de alta, que esta cerrado.
   *
   * El sintoma era desconcertante: al guardar no pasaba nada, sin error y sin
   * ninguna peticion de red. Solo se ve en un navegador; con una sola
   * instancia montada, como en los tests, no ocurre.
   */
  const idFormulario = useId()

  const porDefecto = useMemo(() => {
    if (!gasto) {
      return {
        description: '',
        amount: '',
        expenseDate: hoyISO(),
        category: 'OTROS' as const,
        splitType: 'EQUAL' as const,
        // Por defecto se reparte entre todos, que es el caso habitual.
        participantes: miembros.map((m) => m.userId),
        valores: {} as Record<string, string>,
      }
    }

    // Los gastos anteriores a la migracion V9 no guardaron el valor de cada
    // parte, solo el importe. Para esos se cae a "cantidades exactas" con los
    // importes que ya tenian: es lo unico fiel que se puede ofrecer, porque de
    // unos importes no se recuperan ni los porcentajes ni las partes. El
    // reparto no cambia; lo que cambia es como se describe.
    const faltanValores = gasto.splits.some((s) => s.value === null)
    const tipo =
      gasto.splitType === 'EQUAL' || !faltanValores ? gasto.splitType : ('EXACT' as const)

    return {
      description: gasto.description,
      // Siempre con dos decimales: la API devuelve 40 para 40,00.
      amount: gasto.amount.toFixed(2),
      expenseDate: gasto.expenseDate,
      category: gasto.category,
      splitType: tipo,
      participantes: gasto.splits.map((s) => s.userId),
      valores: Object.fromEntries(
        gasto.splits.map((s) => [
          String(s.userId),
          String(s.value ?? s.amountOwed.toFixed(2)),
        ]),
      ) as Record<string, string>,
    }
  }, [gasto, miembros])

  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({ resolver: zodResolver(esquema), defaultValues: porDefecto })

  const splitType = watch('splitType')
  const participantes = watch('participantes')
  const valores = watch('valores')
  const amount = watch('amount')

  /** El mismo calculo que valida el envio, para mostrarlo mientras se escribe. */
  const cuadre = useMemo(() => {
    const total = aCentimos(amount) ?? 0
    return comprobarCuadre(
      splitType,
      participantes.map((id) => valores[String(id)] ?? ''),
      total,
    )
  }, [splitType, participantes, valores, amount])

  useEffect(() => {
    if (!abierto) reset(porDefecto)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto])

  const alternar = (userId: number) => {
    const dentro = participantes.includes(userId)
    setValue(
      'participantes',
      dentro ? participantes.filter((id) => id !== userId) : [...participantes, userId],
      { shouldValidate: true },
    )
    if (dentro) {
      // Se limpia su importe al salir: dejarlo ahi haria que al volver a
      // marcarle reapareciera una cifra que el usuario ya habia descartado.
      const { [String(userId)]: _fuera, ...resto } = valores
      setValue('valores', resto, { shouldValidate: true })
    }
  }

  const enviar = handleSubmit(async (datos) => {
    const importe = parsearImporte(datos.amount)
    if (importe === null) return

    // Para EQUAL basta la lista de participantes; el backend reparte por mayor
    // residuo. Para los demas modos se envia el valor de cada uno, y es el
    // backend quien lo convierte en importes: aqui no se calcula dinero.
    const splits: SplitInput[] | undefined =
      datos.splitType === 'EQUAL'
        ? undefined
        : datos.participantes.map((userId) => ({
            userId,
            value: Number(String(datos.valores[String(userId)]).replace(',', '.')),
          }))

    try {
      const cuerpo = {
        description: datos.description,
        amount: importe,
        expenseDate: datos.expenseDate,
        category: datos.category,
        splitType: datos.splitType,
        ...(splits ? { splits } : { splitBetweenUserIds: datos.participantes }),
      }

      if (gasto) {
        await actualizar.mutateAsync({ expenseId: gasto.id, datos: cuerpo })
      } else {
        await crear.mutateAsync(cuerpo)
      }
      onCerrar()
    } catch (error) {
      aplicarErrorDeApi(error, setError, CAMPOS)
    }
  })

  /*
    El error de `valores` lo pone superRefine sobre un campo que es un mapa, y
    react-hook-form lo tipa como si pudiera anidar errores por clave. Se
    extrae el texto con una comprobacion explicita en vez de forzar el tipo:
    un cast aqui compilaria igual y se rompe en silencio el dia que el
    error deje de ser plano.
  */
  const errorDeValores = errors.valores?.message
  const mensajeDelReparto =
    errors.participantes?.message ??
    (typeof errorDeValores === 'string' ? errorDeValores : undefined)

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={editando ? 'Editar gasto' : 'Nuevo gasto'}
      pie={
        <>
          <Button variante="secundario" onClick={onCerrar} disabled={isSubmitting}>
            Cancelar
          </Button>
          <Button type="submit" form={idFormulario} cargando={isSubmitting}>
            {isSubmitting ? 'Guardando...' : editando ? 'Guardar cambios' : 'Anadir gasto'}
          </Button>
        </>
      }
    >
      <form id={idFormulario} onSubmit={enviar} className="space-y-4" noValidate>
        {errors.root && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {errors.root.message}
          </p>
        )}

        <Input
          etiqueta="Descripcion"
          autoFocus
          placeholder="Cena del sabado"
          error={errors.description?.message}
          {...register('description')}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            etiqueta="Importe"
            /* type text con inputMode decimal: abre el teclado numerico en
               movil pero deja escribir la coma, que un type=number rechaza
               segun el locale del navegador. */
            type="text"
            inputMode="decimal"
            placeholder="12,50"
            error={errors.amount?.message}
            {...register('amount')}
          />

          <Input
            etiqueta="Fecha"
            type="date"
            error={errors.expenseDate?.message}
            {...register('expenseDate')}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Select etiqueta="Categoria" error={errors.category?.message} {...register('category')}>
            {CATEGORIAS.map((categoria) => (
              <option key={categoria} value={categoria}>
                {ETIQUETA_DE_CATEGORIA[categoria]}
              </option>
            ))}
          </Select>

          <Select etiqueta="Como se reparte" {...register('splitType')}>
            {TIPOS_DE_REPARTO.map((tipo) => (
              <option key={tipo} value={tipo}>
                {MODOS_DE_REPARTO[tipo].etiqueta}
              </option>
            ))}
          </Select>
        </div>

        <SplitEditor
          tipo={splitType}
          miembros={miembros}
          participantes={participantes}
          valores={valores}
          cuadre={cuadre}
          onAlternar={alternar}
          onValor={(userId, valor) =>
            setValue('valores', { ...valores, [String(userId)]: valor }, { shouldValidate: true })
          }
        />

        {mensajeDelReparto && (
          <p role="alert" className="text-sm text-red-600">
            {mensajeDelReparto}
          </p>
        )}
      </form>
    </Modal>
  )
}
