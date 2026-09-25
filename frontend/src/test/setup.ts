import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

/**
 * Cada test arranca con el DOM vacio.
 *
 * Es el equivalente al TRUNCATE del backend: sin esta limpieza los
 * componentes de un test siguen montados en el siguiente, las consultas por
 * texto encuentran dos coincidencias y aparecen fallos que dependen del orden
 * de ejecucion, que son los peores de diagnosticar.
 */
afterEach(() => {
  cleanup()
})

/**
 * `<dialog>` en jsdom: showModal y close no estan implementados.
 *
 * jsdom renderiza el elemento pero no trae sus metodos, asi que cualquier
 * componente que use `<dialog>` revienta con "showModal is not a function"
 * antes de llegar a ninguna asercion. Se implementa lo minimo que necesita
 * `Modal`: alternar la propiedad `open` y emitir el evento `close`, que es
 * lo que el componente escucha para avisar de que se cerro.
 *
 * No pretende reproducir el comportamiento real —ni capa modal, ni Escape,
 * ni la pila de elementos superpuestos—, asi que esas partes se siguen
 * verificando en un navegador. Aqui solo permite probar el contenido y el
 * estado del dialogo.
 */
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function abrir() {
    this.open = true
  }
  HTMLDialogElement.prototype.close = function cerrar() {
    this.open = false
    this.dispatchEvent(new Event('close'))
  }
}
