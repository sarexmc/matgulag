# MathGulag (Fabric 1.21.1)

Versión 1.1.0: arena de gleba, calavera en la cabeza y sonidos de miel.

Port a **Fabric 1.21.1** del mod original de Forge 1.20.1, con HUD y estética renovados siguiendo el
video de referencia. Misma mecánica: gulag tipo "bomba caliente" — una sola pregunta, calavera en la cabeza,
y al acertar debes pasar la bomba golpeando a otro jugador antes de que el temporizador se agote
(si no, explotas y quedas eliminado).

## Requisitos
- Minecraft **1.21.1**
- **Fabric Loader** 0.16 o superior
- **Fabric API** para 1.21.1 (obligatorio, va en `mods` junto al mod)
- Java 21

## Compilar el .jar en GitHub (sin instalar nada)
1. Crea un repositorio nuevo en GitHub.
2. Sube TODO el contenido de esta carpeta (incluida `.github/workflows/build.yml`).
   Si el navegador no sube la carpeta oculta `.github`, crea el archivo a mano:
   "Add file > Create new file", nombre `.github/workflows/build.yml`, y pega el contenido.
3. Ve a la pestaña **Actions** > "Build mod". Si no arranca solo, pulsa **Run workflow**.
4. Cuando termine (verde), entra a la ejecución y descarga el artefacto **mathgulag-fabric-mod**:
   dentro está `mathgulag-fabric-1.1.0.jar`.
5. Pon el `.jar` en la carpeta `mods` de Minecraft 1.21.1 con Fabric (servidor y todos los jugadores),
   junto con Fabric API.

## Compilar en local
Con JDK 21 instalado (el wrapper descarga Gradle solo):

```
./gradlew build        # Linux/macOS
gradlew.bat build      # Windows
```

El jar queda en `build/libs/mathgulag-fabric-1.1.0.jar`.

## Uso
- `/mathgulag <jugadores>`  inicia el gulag (acepta @a, @p, nombres...). Funciona con 1 jugador para probar:
  golpea cualquier criatura para "pasar" la bomba.
- `/mathgulag stop`  detiene el gulag.
- Flujo de una ronda:
  1. El que tiene la bomba ve la pregunta (ej. `8 × 6`) y los dígitos: clic en los números (o teclea)
     para armar la respuesta, como 48. Fallar o agotar los 12 s = explosión + muerte.
  2. Al acertar, el HUD se pone **verde**, la pregunta resuelta queda en pantalla y aparece el banner
     **"¡CORRECTO! Golpea a otro jugador para pasar la bomba · X.Xs"** con la cuenta atrás (10 s).
  3. Si no la pasas a tiempo: explosión y quedas eliminado. Si golpeas a otro jugador del gulag,
     la bomba pasa a él y vuelve la fase de pregunta.

## Bloques de la arena (texturas Gleba)
Ocho bloques `gleba_1` … `gleba_8` (creativo, pestaña **Gulag**), uno por cada textura `Bloque_Gleba*.png`
(16×16, sin animar). Suenan como **bloque de miel** (pisadas y golpes blandos y húmedos, como de órganos).
Los cinco primeros (rojos/carnosos) sueltan esporas carmesí. Incluyen blockstate, modelo, loot table
(sueltan el bloque, sobreviven explosiones) y nombres en inglés/español. No tienen receta.
Sustituyen a los antiguos `meat_block`, `meat_bone_block` y `bone_block`.

## Calavera (sustituye a la TNT)
El portador de la bomba ya no lleva TNT en el casco: se dibuja encima de su cabeza el modelo
`calavera_desafio_4_gulag` (7 texturas en `textures/skull/`), convertido a código en
`client/SkullModel.java`. La dibuja `client/SkullLayer.java` para todos los jugadores que reciben el
estado del gulag. Tu casco real ya no se toca. Ajustes en `SkullLayer`: `SCALE`, `LIFT`, `YAW_OFFSET`.

## Sonidos (todos de miel de Minecraft, sin archivos extra)
`HONEY_BLOCK_STEP/SLIDE/PLACE/HIT/BREAK` y `HONEY_DRINK` con tono grave. Cada dígito suena más agudo que
el anterior (efecto de teclas de órgano). Para cambiarlos: `ClientState.play(...)` y `GulagManager`.

## HUD y animaciones (réplica del video)
- **Red de venas a pantalla completa**: retícula orgánica estilo células/Voronoi que cubre todo el HUD,
  con raíces en los bordes, troncos, capilares y pulsos que recorren las ramas; respira con el latido y
  se tiñe de **verde** al acertar.
- **Pregunta gigante** `2   X   8` con los símbolos separados como en el video, gotas de sangre que gotean,
  escala animada y fondo de humo; la respuesta que vas tecleando aparece debajo, dentro del humo.
  Al acertar se muestra la operación resuelta en verde.
- **Dígitos** con estrella de pinchos roja y negra detrás, aparición escalonada (`easeOutBack`), flotación,
  estela fantasma al presionar, salpicaduras y anillo de impacto.
- **Manos POV** estilo el video: dos puños rojizos asomando desde abajo a los lados, con nudillos y bobbing.
- **Corazón gigante**: al recibir la bomba aparece un corazón enorme que late con gotas cayendo.
- **Feedback**: temblor de pantalla, flashes de daño/fallo/acierto, latido que acelera y sonidos en los
  últimos 3 segundos. La barra de TNT superior se eliminó; el tiempo restante va en la cuenta atrás del
  banner verde.

## Ajustes rápidos
- Tiempos: `QUESTION_TICKS` y `PASS_TICKS` en `game/GulagManager.java` (12 s y 10 s).
- Posiciones de los pinchos: `SLOT_A` / `SLOT_B` en `client/ClientState.java`.
- Texturas de bloques: reemplaza los PNG en `src/main/resources/assets/mathgulag/textures/block/`.

## Cambios del port Forge → Fabric
- Gradle: ForgeGradle → Fabric Loom 1.7.4, Java 17 → 21, mappings oficiales de Mojang.
- `@Mod` → `ModInitializer` / `ClientModInitializer` (entrypoints en `fabric.mod.json`).
- `SimpleChannel` → payloads de 1.21 (`CustomPacketPayload` + `StreamCodec` + `PayloadTypeRegistry`).
- Eventos Forge → Fabric API: tick de servidor/cliente, comandos, golpe a entidad, muerte,
  desconexión y HUD (`HudRenderCallback`).
- Render: `DrawContext` → `GuiGraphics` y el nuevo `Tesselator`/`BufferUploader` de 1.20.5+
  (`buildOrThrow()` protegido para no crashear con buffers vacíos).
- `mods.toml` → `fabric.mod.json`; `pack.mcmeta` a formato 34.

