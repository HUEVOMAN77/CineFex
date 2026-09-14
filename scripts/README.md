# Herramientas de Firestore

## Importar fuentes latino

1. Instala las dependencias:

   ```bash
   cd scripts
   npm install
   ```

2. Crea un archivo JSON con las fuentes que ya verificaste manualmente.
   Usa `movies_links.example.json` como referencia, pero reemplaza el dominio
   de ejemplo por una fuente real y autorizada.

3. Configura la credencial del Firebase Admin SDK fuera del repositorio:

   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="/ruta/segura/service-account.json"
   ```

4. Ejecuta primero una simulación:

   ```bash
   npm run import:movies-links -- ../movies_links.json
   ```

5. Si la validación es correcta, ejecuta la importación:

   ```bash
   npm run import:movies-links -- ../movies_links.json --apply
   ```

El script escribe en `movies_links/{tmdb_id}` y guarda únicamente servidores
con `audio_language` o `language` explícitamente latino. No acepta HTTP,
idiomas vacíos ni etiquetas que solo digan subtitulado o inglés.

No subas el archivo `service-account.json` ni lo pegues en el código. Si la
credencial se filtra, revócala desde Firebase/Google Cloud y genera otra.