# Mis notas Notebook — Windows, teclado y mouse

Lee primero `INSTALAR.txt`, incluido en el paquete, para abrir la aplicación y proteger tus notas.

Versión local para escribir notas, dar formato, insertar imágenes y adjuntar PDF u otros archivos. No contiene controles de lápiz digital.

## Abrir

1. Extraer toda la carpeta del ZIP a una ubicación estable del notebook.
2. Abrir `index.html` con Microsoft Edge o Google Chrome, o hacer doble clic en `Abrir-Mis-Notas.bat`.
3. Crear una nota. El guardado es automático; `Ctrl+S` permite guardarla inmediatamente.

No necesita Android Studio, Node.js ni un servidor. No tiene todavía un instalador EXE. Funciona sin internet y no carga recursos externos.

## Funciones

- Títulos, carpetas y subcarpetas (`Estudio/Biología`), etiquetas, favoritos, búsqueda y ordenación.
- Editor con títulos, negrita, cursiva, subrayado, color, enlaces, listas y tareas.
- Imágenes mediante archivo, pegado o arrastre. Pulsar una imagen para ajustar su ancho o eliminarla.
- PDF y archivos adjuntos, descarga y vista previa de PDF cuando el navegador la permite.
- Duplicación de notas, papelera y restauración.
- Exportación de texto, nota editable con adjuntos y copia completa. Imprimir/guardar PDF desde el diálogo del navegador.
- La copia JSON se restaura añadiendo notas con nuevos identificadores: no sobrescribe las existentes.

## Guardado y Google Drive

Las notas se guardan en IndexedDB del perfil del navegador utilizado. Abrir siempre con el mismo navegador, perfil y ubicación. No usar modo incógnito para conservar notas. Borrar datos del navegador o cambiar de perfil/ubicación puede hacer que la biblioteca deje de estar accesible: mantener copias de seguridad.

**Guardar copia** y **Exportar → Nota editable** permiten elegir un archivo si el navegador ofrece el selector de guardado; en caso contrario lo descargan. Se puede elegir una carpeta sincronizada por Google Drive para escritorio, o subir después la copia desde Drive. **Abrir copia** permite restaurar el JSON descargado desde Drive. La aplicación no inicia sesión en Google ni sincroniza notas automáticamente.

La copia JSON de notebook y la copia ZIP de Android son formatos distintos. No se declara intercambio automático de bibliotecas entre ambas versiones. Para compartir documentos de lectura, usar PDF, texto o imágenes.

## Límites

15 MB por archivo, 40 MB por nota y 128 MB por copia importada. Los archivos permanecen dentro de la base local y la copia editable. Una impresión PDF incluye el texto y las imágenes visibles y una lista de adjuntos; no incorpora las páginas de un PDF adjunto ni los demás archivos binarios. Las copias no están cifradas.

Esta versión se centra en el notebook. No implementa colaboración, sincronización automática, audio, reconocimiento manuscrito ni funciones exclusivas de Samsung.
