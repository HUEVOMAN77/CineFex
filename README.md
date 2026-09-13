# Cinefex - Android Movie Streaming App

Aplicación Android desarrollada en **Kotlin** siguiendo la arquitectura **MVVM**, con integración de la API de **TMDB**, **Firebase Firestore** y un reproductor de video híbrido basado en **WebView** optimizado para dispositivos de media/baja gama (como Redmi 13C y Huawei Nova 10).

---

## 📱 Características Principales

1. **Catálogo Popular (TMDB API)**:
   - Consulta automáticamente `/movie/popular` usando Retrofit.
   - Inyección automática de la clave de API con OkHttp Interceptor.
   - Carga de imágenes optimizada con Coil y cuádricula `RecyclerView`.

2. **Resolución de Enlaces (Firebase Firestore)**:
   - Consulta la colección `movies_links` en Firestore por el ID de TMDB.
   - Extrae el campo `embed_url` o la lista de servidores personalizados.

3. **Lógica de Servidores con Fallback Automático**:
   - Si Firestore no contiene el documento o la lista de servidores está vacía, se generan automáticamente 3 servidores por defecto basados en el ID de TMDB de la película seleccionada:
     * **Ultra** (`SUB`): `https://vidsrc.to/embed/movie/{id}`
     * **Zeus** (`LAT/SUB`): `https://autoembed.to/movie/tmdb/{id}`
     * **Fast** (`SUB`): `https://multiembed.mov/directstream.php?video_id={id}&tmdb=1`

4. **Reproductor Híbrido Fullscreen (`PlayerActivity`)**:
   - Reproducción en pantalla completa con soporte para `WindowInsetsController`.
   - Aceleración por hardware habilitada (`LAYER_TYPE_HARDWARE`).
   - Configuración avanzada de `WebSettings` (`javaScriptEnabled`, `domStorageEnabled`, `setSupportMultipleWindows`, `javaScriptCanOpenWindowsAutomatically`).
   - Bloqueo y descarte automático de ventanas emergentes / pop-ups de publicidad (`WebChromeClient.onCreateWindow`).
   - Restricción de redirecciones fuera del dominio del reproductor (`WebViewClient.shouldOverrideUrlLoading`).

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
