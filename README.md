# Monitor Sísmico — Guía de Ejecución

App Android de **alerta y evacuación** con arquitectura adaptativa:
- **Contexto local:** acelerómetro + GPS
- **Contexto oficial (Opción A):** reportes públicos del **IGP** (capa alineada a difusión **INDECI / SASPe**)
- **Adaptación:** zona segura real (catálogo embebido) + Google Maps + aviso a contacto

---

## 1. Requisitos
* Android Studio Jellyfish / Ladybug (o superior)
* Teléfono físico Android 8+ (API 28+), acelerómetro, GPS, Google Maps
* Internet (para consultar el feed IGP)

---

## 2. Abrir y sincronizar
1. Abre la carpeta del proyecto en Android Studio.
2. Si falla AGP (`9.3.2`), baja a `9.1.0` en `gradle/libs.versions.toml` y Sync.

---

## 3. Permisos del dispositivo
1. Instala con **Run (▶)**.
2. Acepta **Ubicación** y **Notificaciones**.
3. Activa **Aparecer encima / Mostrar sobre otras apps**.
4. En la app usa **Revisar permisos** si el estado queda en amarillo (modo degradado).

---

## 4. Cómo probar

### Sensor local
1. Activa **Monitoreo**.
2. Agita el teléfono (umbral según sensibilidad en **Ajustes**).
3. Solo **EVENTO SÍSMICO** dispara evacuación (golpes/bruscos no).

### Simulación
1. Con monitoreo activo → **Simular Sismo**.

### Feed IGP (INDECI)
1. Con internet, la tarjeta muestra el último reporte IGP.
2. Si aparece un sismo **nuevo**, reciente, con magnitud ≥ umbral del perfil y cerca del usuario, dispara **ALERTA OFICIAL IGP**.
3. La primera consulta solo muestra datos (no alerta por sismos viejos).

### Comercial (casos de uso)
| UC | Qué probar |
|----|------------|
| UC11 GPS retry | Simula sin GPS listo → espera / modo degradado a los ~25 s |
| UC12 Falsos positivos | Perfil Conservador + cooldown 3 min |
| UC13 Silenciar | En alerta → **Silenciar (mantener pantalla)** |
| UC14 Historial | **Historial** + **Falsa alarma** |
| UC15 Zonas reales | Destino con nombre de parque/plaza |
| UC16 Contacto | Ajustes → teléfono → **Avisar contacto** |
| UC17 Sensibilidad | Ajustes → Conservador / Normal / Sensible |
| UC18 Permisos | Quita ubicación → estado degradado / Revisar permisos |
| UC19 IGP | Texto IGP en home + alerta oficial si hay evento nuevo |

---

## 5. Fuente de datos IGP
Endpoint ArcGIS público (Último sismo):

`https://ide.igp.gob.pe/arcgis/rest/services/monitoreocensis/UltimoSismo/MapServer/0/query`

Narrativa del taller: datos de monitoreo **IGP** + capa de preparación/difusión **INDECI**. La app aporta la **adaptación personal** (ruta, UI, sensor local).

---

## 6. Arquitectura
```
MainActivity / Settings / History
        │
 SensorService (foreground)
        │
 ContextManager ─── EventClassifier (perfil)
        │         ─── IgpEarthquakeClient (poll 60s)
        │         ─── EventHistoryStore
        ▼
 AdaptationEngine ─── SafeZoneRepository
        ▼
 AlertaSismoActivity (Maps + SMS/share)
```
