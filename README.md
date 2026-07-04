# Teclado Pegar

Teclado personalizado (IME) minimalista para Android con un botón que pega
el contenido completo del portapapeles en cualquier campo de texto, sin
truncar el contenido, más un botón para cambiar de teclado.

## Cómo abrir el proyecto

1. Clona este repositorio.
2. Ábrelo con Android Studio (Hedgehog o superior recomendado) como proyecto existente.
3. Deja que Gradle sincronice (puede pedir descargar el Android Gradle Plugin y Kotlin).
4. Ejecuta la app (`Run ▶`) en un emulador o dispositivo físico.

## Cómo activar el teclado

1. Abre la app "Teclado Pegar".
2. Toca "Activar teclado en Ajustes" → activa el switch de "Teclado Pegar" en la
   lista de teclados del sistema.
3. Acepta la advertencia estándar de seguridad de teclados de Android.
4. Ve a cualquier app con un campo de texto, mantén presionado el ícono de
   teclado en la barra de navegación (o usa el selector) y elige "Teclado Pegar".

## Estructura

- `MainActivity.kt` – pantalla principal con accesos a Ajustes.
- `MyKeyboardService.kt` – el `InputMethodService` con el botón de pegado completo.
- `res/layout/keyboard_view.xml` – layout del teclado (botón Pegar + switch).
- `res/xml/method.xml` – configuración del IME requerida por Android.

## Notas técnicas

- El pegado usa `InputConnection.commitText()`, el canal oficial que Android
  entrega a cualquier IME activo para escribir en el campo con foco, sin
  importar la app.
- No requiere permisos adicionales en el Manifest más allá de los propios
  de un servicio IME (`BIND_INPUT_METHOD`).
- Para pegados muy grandes se usa `beginBatchEdit()` / `endBatchEdit()`
  para evitar bloqueos de UI en la app receptora.
