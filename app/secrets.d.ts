// Secrets are provided at runtime via `wrangler secret put` (or `.dev.vars`
// locally) and are not part of the `wrangler types` output, so we declare them
// here by merging into the generated global `Env` interface.
declare global {
  interface Env {
    /** HMAC key used to sign the session cookie. */
    SESSION_SECRET: string;
    /** PBKDF2 hash of the single user's login password. */
    APP_PASSWORD_HASH: string;
    /** Base64 AES-256 key for app-layer encryption of R2 attachments. */
    ATTACH_ENCRYPTION_KEY: string;
  }
}

export {};
