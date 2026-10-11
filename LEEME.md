# Mis notas — prototipo Android 0.1

Proyecto inicial para Lenovo Tab M10 Plus (3rd gen). Se ha generado un APK de prueba; falta probarlo en una tablet. No es una reproducción completa de Samsung Notes.

Incluye biblioteca de notas, título y texto, tinta con lápiz digital, azul, resaltador, borrador de trazos, deshacer, importación y navegación de PDF. Guarda localmente al terminar cada trazo y al salir de la pantalla. El texto se guarda al pulsar Guardar o al salir. Las anotaciones se guardan separadas del PDF original.

La superficie acepta eventos de lápiz y omite los dedos. Esto debe comprobarse en el dispositivo: no garantiza el rechazo de palma del hardware. La presión depende de lo que el lápiz comunique a Android. Un lápiz que Android identifique como dedo no dibujará en este prototipo.

## Compilar

Instalar Android Studio, SDK Android 35 y JDK 17. Usar Gradle 8.11.1 con Android Gradle Plugin 8.9.2. Abrir esta carpeta como proyecto y seleccionar Java 17 como JDK de Gradle. Se incluye Gradle Wrapper. Ejecutar `gradlew.bat assembleDebug`. El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar sin cable

Transferir `Mis-notas-Lenovo-0.1.apk` a la tablet mediante un servicio de archivos propio o abrir el enlace de descarga desde la tablet si está disponible. Abrir el APK en el gestor de archivos y, si Android lo solicita, permitir la instalación desde esa aplicación. Instalar y abrir **Mis notas**. Es una compilación de prueba firmada con una clave de desarrollo.

## Pruebas pendientes en tablet

1. Crear nota, escribir texto y tinta, guardar, cerrar aplicación y reabrir.
2. Apoyar la palma al escribir y comprobar que no añade trazos.
3. Importar un PDF de varias páginas, anotar distintas páginas y comprobar persistencia tras reabrir.
4. Probar resaltador, borrado y deshacer; girar la tablet y verificar la conservación de la nota.

## Límites de esta versión

Sin carpetas, búsqueda, sincronización, exportación de PDF anotado, zoom o copias de seguridad. Borrar datos o desinstalar elimina las notas. Renderizado e importación de PDF se realizan en el hilo de interfaz; documentos grandes pueden bloquear momentáneamente la pantalla. El borrador elimina trazos completos al tocar sus puntos. La interfaz y la latencia de escritura requieren validación y mejoras antes de uso habitual.

Referencias técnicas: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer y https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features
