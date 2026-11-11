# Bloques — juego de bloques para Android (gama baja)

Juego nativo en Java, sin librerías ni imágenes: todo se dibuja con Canvas.
Funciona desde Android 4.4 (API 19). El APK final pesa unos pocos cientos de KB.

## Reglas incluidas (guía moderna)
- Tablero 10×20, piezas con colores estándar
- Rotación SRS con *wall kicks* oficiales (incluida la tabla especial de la I)
- Generador "bolsa de 7" (nunca hay sequías largas de una pieza)
- Reserva (hold), 4 piezas siguientes y pieza fantasma
- Lock delay de 0,5 s con hasta 15 reinicios por movimiento
- T-Spin, T-Spin Mini, Back-to-Back (×1,5), combos y limpieza total
- Caída suave (1 pt/celda) y fuerte (2 pts/celda)
- Nivel sube cada 10 líneas; la velocidad sigue la fórmula oficial
- Récord guardado en el teléfono

## Controles
- ◀ ▶ mover (mantener = repetición automática) · ▼ caída suave
- ⤓ caída fuerte · ↺ / ↻ girar · RESERVA guarda la pieza
- Tocar la barra superior o el botón Atrás = pausa

## Cómo obtener el APK

### Opción A — GitHub (sin instalar nada, sirve con PC lento)
1. Crea un repositorio nuevo en github.com.
2. Sube el contenido de esta carpeta (incluida la carpeta oculta `.github`).
   Lo más fácil es con Git: `git init`, `git add .`, `git commit -m "inicio"`, `git push`.
3. Entra a la pestaña **Actions** del repositorio: se ejecuta "Compilar APK" solo (≈3–5 min).
4. Abre la ejecución terminada y descarga el artefacto **Bloques-APK** (es un .zip con `Bloques.apk`).
5. Pasa el APK al teléfono e instálalo (permite "instalar apps de origen desconocido").

### Opción B — Android Studio
1. File › Open › selecciona esta carpeta.
2. Build › Build App Bundle(s) / APK(s) › Build APK(s).
3. El APK queda en `app/build/outputs/apk/`.

El APK de release está firmado con la clave de depuración para que se pueda instalar
directamente. Para publicarlo en Play Store hay que firmarlo con una clave propia
(y cambiarle el nombre: "Tetris" es marca registrada, por eso aquí se llama "Bloques").
