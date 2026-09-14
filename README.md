# Cinefex - Android Movie Streaming App

Aplicación Android desarrollada en **Kotlin** siguiendo la arquitectura **MVVM**, con integración de la API de **TMDB**, **Firebase Firestore** y un reproductor de video híbrido basado en **WebView** optimizado para dispositivos de media/baja gama (como Redmi 13C y Huawei Nova 10).

---

## 📱 Características Principales

1. **Catálogo Popular (TMDB API)**:
   - Consulta automáticamente `/movie/popular` usando Retrofit.
   - Solicita títulos y sinopsis en **es-MX** y prioriza estrenos de la región **MX**.
   - Inyección automática de la clave de API con OkHttp Interceptor.
   - Carga de imágenes optimizada con Coil y cuádricula `RecyclerView`.

2. **Resolución de Enlaces (Firebase Firestore)**:
   - Consulta la colección `movies_links` en Firestore por el ID de TMDB.
   - Extrae el campo `embed_url` o la lista de servidores personalizados.

3. **Fuentes de reproducción**:
   - Solo se muestran servidores cuya metadata declara explícitamente `Latino`, `LATAM`, `es-419`, `es-LAT` o `es-MX`.
   - Ya no se agregan fuentes genéricas con `?lang=lat` como si eso garantizara audio latino.
   - Si ninguna fuente confirma español latino, la app informa que no hay un servidor compatible en lugar de enviar al usuario a una fuente en inglés.
   - Para Firestore, cada servidor debe incluir `embed_url` y `audio_language` (o `language`) con un valor latino explícito. También puede incluir `is_latino: true`, pero la etiqueta de idioma sigue siendo obligatoria.

4. **Reproductor Híbrido Fullscreen (`PlayerActivity`)**:
   - Reproducción en pantalla completa con soporte para `WindowInsetsController`.
   - Aceleración por hardware habilitada (`LAYER_TYPE_HARDWARE`).
   - Configuración avanzada de `WebSettings` (`javaScriptEnabled`, `domStorageEnabled`, `setSupportMultipleWindows`, `javaScriptCanOpenWindowsAutomatically`).
   - Bloqueo y descarte automático de ventanas emergentes / pop-ups de publicidad (`WebChromeClient.onCreateWindow`).
   - Bloqueo de esquemas externos como `intent://`; la app no puede convertir audio inglés a español si el embed no ofrece una pista de audio o subtítulos en español.

---

## 📦 Descarga del APK Compilado

El APK de depuración se compila en el proyecto en la siguiente ruta:

- **Ruta local del APK**: `app/build/outputs/apk/debug/app-debug.apk`

---

## 🛠️ Requisitos e Instalación

- **JDK**: Java 17
- **SDK Objetivo**: Android 34 (Android 14)
- **Min SDK**: Android 24 (Android 7.0)
- **Gradle**: 8.8

### Compilación local:

```bash
# Correr pruebas unitarias
./gradlew test

# Compilar APK de depuración
./gradlew assembleDebug
```
