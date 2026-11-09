import type { FieldValues, Resolver } from 'react-hook-form'

/**
 * Pasa unos datos por el resolver de un formulario, como hace react-hook-form
 * al enviar, y devuelve lo que llegaria a la pantalla.
 *
 * Se prueba a traves del resolver y no con `safeParse` porque es el resolver
 * quien decide que se muestra: de todos los fallos de un campo, el formulario
 * pinta solo el primero. Afirmar sobre la lista completa de zod fijaria un
 * detalle que el usuario nunca ve, y dejaria sin fijar el mensaje que si ve.
 *
 * `valores` son los datos ya transformados (recortados, por ejemplo), que son
 * los que recibe el manejador de envio y acaban en la peticion.
 */
export async function validar<T extends FieldValues>(resolver: Resolver<T>, datos: T) {
  const { errors, values } = await resolver(datos, undefined, {
    fields: {},
    shouldUseNativeValidation: false,
  })

  const mensajes: Record<string, unknown> = {}
  for (const [campo, error] of Object.entries(errors)) {
    mensajes[campo] = error && 'message' in error ? error.message : error
  }

  return { mensajes, valores: values }
}
