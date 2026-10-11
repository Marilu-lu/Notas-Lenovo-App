# Mis notas — Android y Notebook

## Versión para notebook Samsung / Windows

La carpeta [desktop/](desktop/README.md) contiene una versión local centrada en teclado y mouse: editor con formato, imágenes, PDF y archivos adjuntos, organización, búsqueda y respaldo. Abrir `desktop/index.html` con Edge o Chrome. No requiere Android Studio y no contiene controles de lápiz.

Las versiones Android y notebook tienen bibliotecas locales y formatos de respaldo diferentes. El módulo desktop no establece sincronización automática entre ambas. Sus pruebas de lógica se ejecutan con `node desktop/tests/core.test.js`; la prueba visual y la persistencia en navegador siguen pendientes porque el navegador de revisión bloqueó el protocolo de archivos locales.

## Versión Android 0.2

Aplicación para Lenovo Tab M10 Plus (3rd gen), con diseño e implementación propios. El objetivo es ampliar la cobertura funcional de una aplicación de notas como Samsung Notes. **No se declara equivalencia al 100 %.** Ver [FUNCIONES.md](FUNCIONES.md).

## Funciones de esta versión

- Biblioteca: búsqueda de título/texto/etiquetas, orden por título o modificación, favoritos, carpetas y subcarpetas mediante rutas (`Estudio/Biología`), duplicación, papelera y restauración.
- Editor: tinta a presión, resaltador, colores, tres grosores, borrador de trazos y de área, deshacer/rehacer por gesto, selección rectangular, mover, copiar, pegar, eliminar y redimensionar trazos e imágenes.
- Navegación: pellizco para zoom, herramienta para desplazar la hoja, modo solo lápiz o también dedo.
- Páginas de cuaderno: crear, duplicar, mover y eliminar; fondo blanco, rayado, cuadrícula o puntos. Geometría de página estable al girar la pantalla.
- Texto: negrita, cursiva y subrayado sobre una selección, conservados en la nota editable; guardado tras una pausa de escritura o al salir.
- Adjuntos: PDF e imágenes importados desde el selector de Android; imágenes incluidas en la nota editable y su copia.
- Reconocimiento de manuscritos en español con ML Kit, sobre la selección o la página. Revisión del resultado antes de añadirlo al texto. Requiere una primera descarga del modelo; posteriormente reconoce en el dispositivo.
- Exportación: PDF con tinta, figuras e imágenes, texto TXT y nota editable ZIP. Copia completa y restauración que añade notas con nuevos identificadores sin sobrescribir las existentes.

## Google Drive

La aplicación usa el **Storage Access Framework** de Android. No incorpora un inicio de sesión de Google propio, claves OAuth ni acceso completo a Drive.

1. Instalar/iniciar sesión en Google Drive en la tablet.
2. Usar **Abrir PDF / Drive**, **Insertar imagen / Drive** o **Restaurar copia**; en el selector de Android, elegir Drive y el archivo.
3. Usar **Más → Guardar PDF / Drive**, **Guardar texto / Drive**, **Guardar nota editable / Drive**, o **Copia / Drive** desde la biblioteca. Elegir Drive en el selector de destino si el proveedor lo ofrece.
4. Para volver a editar todas las capas, guardar una nota editable ZIP y abrirla mediante **Restaurar copia**. Un PDF exportado es una copia para lectura; no conserva las capas de edición de la aplicación.

La disponibilidad de Drive depende de su proveedor de documentos y de la configuración de Android. Si no aparece como destino, guardar el archivo localmente y subirlo desde Drive. **No hay sincronización automática, seguimiento de cambios ni resolución de conflictos.** Cada importación crea una copia local y cada exportación crea un archivo nuevo.

Referencia: https://developer.android.com/guide/topics/providers/document-provider

## Compilar

Android SDK 35, JDK 17, Gradle Wrapper 8.11.1 y AGP 8.9.2. Abrir el proyecto en Android Studio y seleccionar Java 17 como JDK de Gradle.

```powershell
.\gradlew.bat assembleDebug lintDebug
```

El APK está en `app/build/outputs/apk/debug/app-debug.apk`. La primera compilación descarga dependencias. El APK de prueba usa firma de desarrollo; instalar una actualización con otra clave requiere un procedimiento de migración, no desinstalar sin respaldo.

## Comprobaciones

Las pruebas de `tests/EditHistoryCheck.java` verifican recuperación después de borrar, rehacer, nueva edición y cancelación. `tests/NoteArchivesCheck.java` verifica restauración sin sobrescritura, referencias a PDF, metadatos, archivos dañados y nombres que intentan salir del directorio de restauración.

`app/src/androidTest/` incluye comprobaciones de editor, PDF y respaldo para ejecutar en un dispositivo o emulador mediante `adb shell am instrument -w cl.marilu.notas.test/cl.marilu.notas.NotesSmokeTest`. No sustituye las pruebas con el lápiz real ni con una cuenta de Drive.

## Límites actuales

- Pendientes: audio sincronizado, cifrado/bloqueo por nota, lazo, alineación de manuscritos, búsqueda dentro de PDF/manuscritos, miniaturas, desplazamiento continuo, escáner, impresión, exportación Word/PowerPoint y asistentes de IA.
- Las páginas originales de PDF se conservan en orden y no se reordenan/eliminan desde esta versión. Exportarlas renderiza su fondo como imagen: no preserva su texto seleccionable, enlaces, formularios ni metadatos. La tinta añadida se dibuja sobre ese fondo.
- El texto de teclado se exporta en páginas adicionales del PDF, con formato plano. No se mezcla como texto editable sobre la hoja de tinta.
- Deshacer/rehacer corresponde a tinta y objetos de la página actual; no recupera páginas eliminadas ni cambios de metadatos. El borrador de área fragmenta tinta, pero elimina figuras completas; la selección incluye objetos cuando abarca alguno de sus puntos.
- Límites de importación/restauración: 32 MB por archivo y 128 MB por copia restaurada. Las imágenes se reducen para limitar memoria.
- La copia ZIP **no está cifrada**. Contiene también la papelera. No hay vaciado automático ni borrado definitivo desde la interfaz.
- La presión, el rechazo de palma, los gestos, la velocidad y el reconocimiento deben verificarse en la Lenovo. El emulador del PC no pudo arrancar porque falta aceleración por hipervisor.
- Borrar los datos o desinstalar elimina la biblioteca local. Guardar una copia antes de cualquier migración.

No se utilizan recursos, código o servicios privados de Samsung. Sus funciones de nube, Galaxy AI y controles específicos de S Pen requieren alternativas propias.
