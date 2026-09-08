# Guía de Ejecución - Monitor Sísmico

Instrucciones para compilar, instalar y probar la aplicación en un dispositivo Android.

---

## 1. Requisitos Previos
*   **Android Studio:** Jellyfish / Ladybug (o superior).
*   **Dispositivo de prueba:** Teléfono físico con **Android 8.0 (API 26)** o superior, sensor acelerómetro, GPS activado y la aplicación Google Maps instalada.

---

## 2. Configuración en Android Studio

### Abrir el proyecto
*   Abre Android Studio, selecciona **Open** y elige la carpeta del proyecto.

### Solución a errores de compatibilidad de Gradle (AGP)
Si al sincronizar aparece el error: `The project is using an incompatible version (AGP 9.3.2)...`
1.  Abre el archivo `gradle/libs.versions.toml` (o `build.gradle.kts`).
2.  Cambia la versión de **agp** de `9.3.2` a `9.1.0` (o la versión soportada por tu Android Studio).
3.  Haz clic en **Sync Now** en la barra superior.

---

## 3. Configuración Obligatoria del Dispositivo
Para que la alerta emergente pueda desplegarse desde segundo plano o con la pantalla bloqueada, debes otorgar el permiso de superposición manualmente:
1.  Conecta el celular a la PC e instala la app presionando **Run (▶)** en Android Studio.
2.  En tu teléfono, mantén presionado el ícono de la aplicación instalada y entra a **Información de la aplicación** (ícono ℹ️).
3.  Busca la opción **Aparecer encima** (o *Mostrar sobre otras aplicaciones* / *Draw over other apps*).
4.  Activa el interruptor a **Permitido**.

---

## 4. Instrucciones para Ejecutar y Probar

### Prueba 1: En Primer Plano
1.  Abre la aplicación en el teléfono.
2.  Acepta los permisos de **Ubicación** y **Notificaciones** cuando la app los solicite.
3.  Activa el interruptor principal de la pantalla (**Monitoreo ACTIVO**).
4.  Agita el teléfono con firmeza para superar el umbral de aceleración (**13.0 m/s²**).
5.  Aparecerá la ventana de alerta. Presiona **"Ver ruta óptima de evacuación"** para abrir Google Maps.

### Prueba 2: En Segundo Plano o Pantalla Bloqueada
1.  Con el interruptor activado, minimiza la aplicación o bloquea la pantalla del celular.
2.  Agita el teléfono con firmeza.
3.  La pantalla se encenderá automáticamente mostrando la tarjeta emergente de alerta para iniciar la evacuación.
