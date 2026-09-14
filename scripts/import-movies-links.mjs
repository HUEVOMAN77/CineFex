#!/usr/bin/env node

import fs from "node:fs/promises";
import process from "node:process";
import { initializeApp, applicationDefault, getApps } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const COLLECTION = "movies_links";
const FIRESTORE_BATCH_SIZE = 450;
const LANGUAGE_HINTS = [
  "es-419",
  "es-lat",
  "es-mx",
  "español latino",
  "espanol latino",
  "latino",
  "latam",
];

function printUsage() {
  console.log(`
Uso:
  node scripts/import-movies-links.mjs <archivo.json> [--apply]

Por defecto solo valida y muestra lo que se importaría.
--apply   escribe los documentos en Firestore.

El archivo debe contener un arreglo con esta forma:
[
  {
    "tmdb_id": 550,
    "servers": [
      {
        "name": "Servidor Latino 1",
        "embed_url": "https://servidor-autorizado.example/embed/550",
        "audio_language": "es-419"
      }
    ]
  }
]

Credenciales:
  GOOGLE_APPLICATION_CREDENTIALS=/ruta/al/service-account.json
`);
}

function hasLatinoLanguage(language) {
  const normalized = String(language ?? "").trim().toLowerCase();
  return LANGUAGE_HINTS.some((hint) => normalized.includes(hint));
}

function normalizeServer(server, recordIndex, serverIndex) {
  const name = String(server.name ?? "").trim();
  const embedUrl = String(server.embed_url ?? server.embedUrl ?? "").trim();
  const audioLanguage = String(
    server.audio_language ?? server.language ?? "",
  ).trim();

  if (!name) {
    throw new Error(
      `Registro ${recordIndex + 1}, servidor ${serverIndex + 1}: falta name.`,
    );
  }

  if (!embedUrl.startsWith("https://")) {
    throw new Error(
      `Registro ${recordIndex + 1}, servidor ${serverIndex + 1}: embed_url debe usar HTTPS.`,
    );
  }

  if (!hasLatinoLanguage(audioLanguage)) {
    throw new Error(
      `Registro ${recordIndex + 1}, servidor ${serverIndex + 1}: ` +
        `audio_language "${audioLanguage}" no confirma español latino.`,
    );
  }

  return {
    name,
    embed_url: embedUrl,
    audio_language: audioLanguage,
    is_latino: true,
  };
}

function normalizeRecord(record, index) {
  const tmdbId = Number(record.tmdb_id ?? record.tmdbId ?? record.id);
  if (!Number.isSafeInteger(tmdbId) || tmdbId <= 0) {
    throw new Error(`Registro ${index + 1}: tmdb_id debe ser un entero positivo.`);
  }

  if (!Array.isArray(record.servers) || record.servers.length === 0) {
    throw new Error(
      `Registro ${index + 1} (${tmdbId}): servers debe tener al menos un servidor.`,
    );
  }

  const servers = record.servers.map((server, serverIndex) =>
    normalizeServer(server, index, serverIndex),
  );

  return {
    tmdbId,
    document: {
      servers,
      source: "manual-latino-import",
      updated_at: FieldValue.serverTimestamp(),
    },
  };
}

async function loadRecords(filePath) {
  const raw = await fs.readFile(filePath, "utf8");
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) {
    throw new Error("El archivo JSON debe contener un arreglo de películas.");
  }

  const records = parsed.map(normalizeRecord);
  const ids = new Set();
  for (const record of records) {
    if (ids.has(record.tmdbId)) {
      throw new Error(`El tmdb_id ${record.tmdbId} aparece más de una vez.`);
    }
    ids.add(record.tmdbId);
  }
  return records;
}

async function main() {
  const positionalArgs = process.argv.slice(2);
  if (positionalArgs.length === 0 || positionalArgs.includes("--help")) {
    printUsage();
    return;
  }

  const filePath = positionalArgs.find((arg) => !arg.startsWith("--"));
  const apply = positionalArgs.includes("--apply");
  if (!filePath) {
    printUsage();
    process.exitCode = 1;
    return;
  }

  const records = await loadRecords(filePath);
  console.log(`Validados ${records.length} documento(s) de ${COLLECTION}.`);
  for (const record of records) {
    console.log(
      `- ${record.tmdbId}: ${record.document.servers.length} fuente(s) latino`,
    );
  }

  if (!apply) {
    console.log("\nSimulación terminada. Usa --apply para escribir en Firestore.");
    return;
  }

  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    throw new Error(
      "Falta GOOGLE_APPLICATION_CREDENTIALS. No pegues la credencial en el repositorio.",
    );
  }

  if (getApps().length === 0) {
    initializeApp({ credential: applicationDefault() });
  }

  const db = getFirestore();
  for (let start = 0; start < records.length; start += FIRESTORE_BATCH_SIZE) {
    const batch = db.batch();
    const chunk = records.slice(start, start + FIRESTORE_BATCH_SIZE);
    for (const record of chunk) {
      const ref = db.collection(COLLECTION).doc(String(record.tmdbId));
      batch.set(ref, record.document, { merge: true });
    }
    await batch.commit();
  }
  console.log(`\nImportación completada: ${records.length} documento(s) escritos.`);
}

main().catch((error) => {
  console.error(`\nError: ${error.message}`);
  process.exitCode = 1;
});