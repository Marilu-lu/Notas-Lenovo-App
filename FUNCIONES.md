# Cobertura funcional — Mis notas

Objetivo: funciones de notas equivalentes a Samsung Notes, con implementación y diseño propios, para Lenovo Tab M10 Plus (3rd gen). Prioridad elegida: datos locales y copia de seguridad. Este documento no declara equivalencia completa ni valida el lápiz físico.

## Referencia oficial

- https://www.samsung.com/us/support/answer/ANS10004548/ — organización, páginas, plantillas y PDF.
- https://www.samsung.com/us/support/answer/ANS10003634/ — escritura, selección, borrado, conversión y zoom.
- https://www.samsung.com/us/support/answer/ANS10001577/ — audio y reproducción vinculada a notas.
- https://www.samsung.com/us/support/answer/ANS10002404/ — formatos de exportación.

## Requisitos de aceptación

| Grupo | Función objetivo | Validación requerida |
|---|---|---|
| Biblioteca | Carpetas y subcarpetas, búsqueda, ordenar, etiquetas, favoritos | Encontrar y mover notas; conservar organización tras reinicio |
| Biblioteca | Duplicar, papelera y restaurar | Recuperar una nota eliminada sin perder contenido |
| Editor | Texto con formato, lápiz, colores y grosores | Conservar formato y trazos en todas las páginas |
| Editor | Resaltador, borrador de trazo y de área, deshacer y rehacer | Recuperar contenido eliminado por cada herramienta |
| Editor | Selección por lazo/rectángulo, mover, copiar, cortar y cambiar tamaño | Conservar ubicación y estilo después de guardar |
| Editor | Formas, alineación y limpieza de manuscritos | Mantener intención y contenido de la escritura |
| Editor | Zoom y desplazamiento, dedo/lápiz, presión y palma | Pruebas con lápiz real Lenovo y rotación de pantalla |
| Páginas | Añadir, duplicar, eliminar, reordenar; miniaturas | Conservar el orden, las anotaciones y los adjuntos |
| Páginas | Plantillas, colores, tamaño, páginas y desplazamiento continuo | Conservar geometría del contenido al cambiar orientación |
| Adjuntos | Imágenes, escáner, voz y archivos | Mantener adjuntos en copia de seguridad y exportación |
| PDF | Importar, anotar, exportar, compartir e imprimir | PDF exportado visible en otra aplicación con todas las páginas |
| Exportar | PDF, imagen, texto, Word y PowerPoint | Verificación independiente de los formatos generados |
| Manuscritos | Reconocimiento en español, conversión y búsqueda | Modelo local; evaluar precisión con escritura real |
| Audio | Grabación, reproducción, marcas temporales y velocidad | Relacionar el audio con los trazos y texto correspondientes |
| Protección | Bloqueo y cifrado por nota | No almacenar contenido o adjuntos protegidos en texto claro |
| Respaldo | Exportar y restaurar biblioteca completa | Restauración transaccional y pruebas con archivos dañados |
| IA | Resumen, organización, traducción, transcripción y matemáticas | Alternativas locales y evaluación de recursos de la tablet |
| Integraciones | Sincronización, colaboración y acceso en PC | Fuera de la configuración local solicitada; requiere servicio propio |
| Hardware | Botones/hover del lápiz, notas con pantalla apagada, accesos rápidos | Depende del lápiz, Android y firmware Lenovo |

Los servicios de Samsung Cloud, Galaxy AI y las integraciones privadas de S Pen no pueden asumirse disponibles en Lenovo. No se simularán funciones no implementadas mediante botones que aparenten funcionar.

## Estado de la versión 0.2

Implementado en código: biblioteca y organización básica, favoritos, papelera, texto con tres estilos, tinta/figuras/imágenes, borrado, historial, selección rectangular y transformaciones, zoom, páginas de cuaderno y cuatro plantillas, conversión de manuscritos en español, importación PDF, exportación PDF/TXT/ZIP, copia y restauración.

Google Drive: importación y exportación manual mediante el proveedor de documentos de Android. La edición sigue siendo local; no se implementó sincronización automática. Esto incorpora la petición posterior de guardar y abrir documentos desde Drive sin cambiar la prioridad local de la biblioteca.

Pendiente de implementación: audio vinculado, bloqueo cifrado, lazo y mejoras de manuscritos, búsqueda en PDF/tinta, miniaturas/hoja continua, escáner e impresión, Word/PowerPoint, IA local, integraciones propias de hardware y sincronización completa. PDF con fondo rasterizado y texto de teclado separado son limitaciones de esta primera exportación.

Pendiente de pruebas físicas: lápiz Lenovo, presión, palma, rendimiento, reconocimiento español, proveedor Drive y selección de archivos en la tablet. El emulador no arrancó por falta de hipervisor, por lo que las pruebas de dispositivo no se presentan como ejecutadas.
