# MeetRec 🎙️📋

**MeetRec** es una aplicación móvil nativa para Android diseñada para capturar reuniones de trabajo, transcribirlas con inteligencia artificial (Whisper) y generar de forma inmediata un **informe ejecutivo maquetado en PDF** con los puntos tratados, acuerdos y el plan de acción (To-Do list) con tareas y responsables.

A diferencia de otras soluciones, **funciona sin servidores propios ni infraestructura VPS**: la generación del PDF se procesa de forma 100% nativa en el dispositivo y la IA se comunica directamente mediante tu propia clave API.

---

## ✨ Características Principales

1. **Captura de Audio Continua y Fiable:**
   * Motor nativo `AudioRecord` (16 kHz, 16-bit PCM Mono).
   * Grabación mediante `ForegroundService` con notificación persistente: graba con la pantalla apagada o con la app en segundo plano.
   * Segmentación automática en bloques WAV seguros de 5 minutos: si la batería se agota o entra una llamada, el audio grabado nunca se pierde.
   * Vúmetro visual en decibelios (dB RMS) en tiempo real con detección de saturación (*clipping*).

2. **Transcripción Inteligente con Whisper:**
   * Utiliza el modelo `openai/whisper-large-v3` a través de la API de OpenRouter.
   * Segmenta y ordena el texto con marcas de tiempo exactas (`[00:02:15]`).

3. **Resumen Ejecutivo y Plan de Acción (To-Do List):**
   * Sintetiza la reunión con modelos avanzados y económicos (por defecto `GPT-4o mini`, con soporte para `Claude 3.5 Haiku` o `Llama 3.3 70B`).
   * Extrae automáticamente:
     * 🎯 **Objetivo de la reunión** (propósito central).
     * 📋 **Puntos tratados y discusión** (resumen por temas).
     * 🤝 **Acuerdos clave** alcanzados.
     * ⚡ **Plan de acción / To-Do List** con columnas estructuradas: *Tarea*, *Responsable* y *Plazo*.

4. **Exportación de PDF 100% Nativa (Cero VPS):**
   * Maqueta en local un documento A4 corporativo limpio y elegante.
   * **Página 1:** Informe ejecutivo estructurado y tabla de compromisos.
   * **Páginas siguientes:** Transcripción completa de respaldo con marcas de tiempo.
   * Apertura inmediata con el visor de PDF del móvil o envío directo por WhatsApp, Slack, Correo o Google Drive mediante `FileProvider`.

5. **Privacidad y Control de Costes:**
   * No requiere crear cuentas en servicios de terceros ni pasar por un backend propio.
   * Introduces tu propia clave de OpenRouter en Ajustes (icono ⚙️). La clave se guarda de forma segura y privada en el almacenamiento local de tu teléfono.

---

## 🚀 Instalación Rápida (APK)

En la raíz de este repositorio dispones del archivo precompilado:

👉 **[`MeetRec.apk`](./MeetRec.apk)** (19 MB)

1. Descarga el archivo `MeetRec.apk` en tu teléfono Android.
2. Al abrirlo, autoriza la instalación desde fuentes desconocidas si tu navegador o gestor de archivos te lo solicita.
3. Abre MeetRec y concédele permiso de **Micrófono** y **Notificaciones**.
4. En el diálogo inicial o pulsando el icono de **Ajustes (⚙️)** en la cabecera, introduce tu API Key de OpenRouter (`sk-or-v1-...`).

### ¿Cómo conseguir tu clave API de OpenRouter?
1. Entra en [openrouter.ai](https://openrouter.ai/) y regístrate con tu correo o cuenta de Google.
2. Ve a la sección **Keys** y crea una clave nueva.
3. Añade una pequeña recarga (por ejemplo 3 o 5 €). Con eso tendrás para transcribir y resumir decenas de reuniones de trabajo (el coste aproximado es de ~0,05 € a 0,15 € por hora de reunión).

---

## 🛠️ Tecnologías y Arquitectura

* **Lenguaje:** Kotlin 1.9.22
* **Interfaz de Usuario:** Jetpack Compose con Material 3 (paleta corporativa Slate & Indigo)
* **Motor de Audio:** `android.media.AudioRecord` + rotación de encabezados RIFF/WAV
* **Reproductor:** AndroidX Media3 ExoPlayer
* **Red:** OkHttp 4.12.0
* **PDF Engine:** `android.graphics.pdf.PdfDocument` nativo (sin dependencias externas)
* **Compatibilidad:** Android 8.0 (API 26) o superior

---

## 💻 Compilación desde el Código Fuente

Si deseas modificar la aplicación o compilarla en tu equipo:

1. Clona el repositorio:
   ```bash
   git clone https://github.com/tortextrx/meetrec.git
   cd meetrec
   ```
2. Ábrelo en **Android Studio** (Hedgehog o superior) con JDK 17.
3. Compila el APK desde la terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   El APK generado se encontrará en:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 Licencia

Uso libre y modificable según tus necesidades operativas y profesionales.
