#!/usr/bin/env node
// Generates an APP_PASSWORD_HASH value compatible with app/lib/auth/password.ts.
// Usage: node scripts/hash-password.mjs <password>
const password = process.argv[2];
if (!password) {
  console.error("usage: node scripts/hash-password.mjs <password>");
  process.exit(1);
}

const iterations = 100_000;
const salt = crypto.getRandomValues(new Uint8Array(16));
const keyMaterial = await crypto.subtle.importKey(
  "raw",
  new TextEncoder().encode(password),
  "PBKDF2",
  false,
  ["deriveBits"],
);
const bits = new Uint8Array(
  await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations, hash: "SHA-256" },
    keyMaterial,
    256,
  ),
);
const b64 = (u8) => Buffer.from(u8).toString("base64");
process.stdout.write(`pbkdf2$${iterations}$${b64(salt)}$${b64(bits)}\n`);
