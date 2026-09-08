# 🚨 Monitor Sísmico & Sistema Adaptativo de Evacuación

![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android SDK](https://img.shields.io/badge/SDK-24+-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Material Design 3](https://img.shields.io/badge/Material--Design-3-757575?style=for-the-badge&logo=material-design&logoColor=white)

## 📌 Introducción
**Monitor Sísmico** es una solución avanzada de seguridad personal diseñada para dispositivos Android. La aplicación utiliza el hardware del dispositivo para detectar vibraciones sísmicas en tiempo real y, mediante algoritmos de computación adaptativa, guía al usuario hacia la zona segura más cercana de manera autónoma.

A diferencia de las alertas convencionales, este sistema combina **fusión de sensores (Acelerómetro + GPS)** y la **fórmula de Haversine** para calcular rutas de evacuación dinámicas, funcionando incluso cuando el dispositivo está bloqueado.

---

## ✨ Características Principales

- 📡 **Detección en Tiempo Real:** Monitoreo constante del acelerómetro con un umbral de activación calibrado a **13.0 m/s²**.
- 📍 **Fusión de Contexto:** Integración de *Google Fused Location Provider* para obtener coordenadas precisas en el momento exacto del evento.
- 📐 **Cálculo de Evacuación:** Implementación de la fórmula de Haversine para determinar la distancia geodésica a zonas seguras locales.
- 🛡️ **Foreground Service:** Garantiza que el monitoreo no sea interrumpido por el sistema Android, manteniendo un consumo energético optimizado.
- 🔓 **Alerta de Alta Prioridad:** Interrupción del bloqueo de pantalla y encendido automático del panel visual ante emergencias críticas.

---

## 🏗️ Arquitectura del Proyecto

El proyecto sigue una estructura modular orientada a servicios y motores de reglas:

```text
app/src/main/
├── java/com/example/myapplication/
│   ├── MainActivity.kt           # Panel de control y Dashboard (MD3)
│   ├── SensorService.kt          # Servicio de primer plano (Monitoring Core)
│   ├── ContextManager.kt         # Gestión de sensores y calibración G-Force
│   ├── AdaptationEngine.kt       # Motor de reglas y cálculo de rutas Haversine
│   └── AlertaSismoActivity.kt    # Interfaz de emergencia (System Alert Window)
└── res/
    ├── layout/
    │   ├── activity_main.xml     # Dashboard con Material Design 3
    │   └── activity_alerta_sismo.xml # Diálogo de alta visibilidad
    └── values/                   # Definiciones de estilos y temas
```

---

## 🛠️ Tecnologías y Requisitos

- **Lenguaje:** [Kotlin](https://kotlinlang.org/)
- **Min SDK:** 24 (Android 7.0 Nougat)
- **Target SDK:** 34 (Android 14)
- **Dependencias Clave:**
  - `com.google.android.gms:play-services-location`: Geolocalización precisa.
  - `com.google.android.material:material`: Componentes de UI modernos.
- **Gradle Plugin (AGP):** 9.1.0 - 9.3.2.

---

## 🚀 Instalación y Configuración

### 1. Clonar el repositorio
```bash
git clone https://github.com/tu-usuario/monitor-sismico-android.git
```

### 2. Solución a errores de AGP/Gradle
Si encuentras el error `Unsupported class file major version`, asegúrate de tener configurado **JDK 17 o superior** en Android Studio:
`Settings` > `Build, Execution, Deployment` > `Build Tools` > `Gradle` > `Gradle JDK`.

### 3. Permisos Críticos 🔑
Para que el sistema funcione correctamente, es **obligatorio** conceder los siguientes permisos tras la instalación:
1. **Ubicación (ACCESS_FINE_LOCATION):** Configurar como "Permitir siempre".
2. **Notificaciones (POST_NOTIFICATIONS):** Necesario para el servicio de monitoreo activo.
3. **Aparecer encima (SYSTEM_ALERT_WINDOW):** 
   - Ve a `Ajustes` > `Aplicaciones` > `Monitor Sísmico`.
   - Busca `Aparecer encima` o `Mostrar sobre otras aplicaciones`.
   - Activa el interruptor (Vital para la alerta en pantalla bloqueada).

---

## 🧪 Guía de Pruebas (Step-by-Step)

### Prueba 1: Monitoreo en Primer Plano
1. Abre la aplicación y activa el interruptor de **Monitoreo**.
2. Verás una notificación persistente indicando que el sistema está activo.
3. Agita el dispositivo con fuerza para superar el umbral de **13.0 m/s²**.
4. La aplicación lanzará automáticamente el mapa con la ruta de evacuación.

### Prueba 2: Pantalla Bloqueada y Segundo Plano
1. Con el monitoreo activo, sal de la aplicación o bloquea el teléfono.
2. Realiza una sacudida brusca (simulación de sismo).
3. El sistema encenderá la pantalla automáticamente (`setTurnScreenOn`) y mostrará el diálogo de emergencia sobre el *lockscreen*.

---

## 📄 Licencia
Este proyecto está bajo la Licencia MIT. Consulta el archivo `LICENSE` para más detalles.

---
**Desarrollado con fines preventivos y de seguridad ciudadana.** 🌎🛡️
