/**
 * Time-sortable identifiers (UUIDv7).
 *
 * UUIDv7 lays a 48-bit big-endian Unix-millis timestamp in the high bits, so ids
 * sort by creation time lexicographically. That lets any client (web, mobile,
 * offline) mint an id locally without a server round-trip, and lets the id double
 * as a stable pagination/sync cursor. The format is a standard RFC 9562 UUID
 * string, so it stays a drop-in for the existing `text` primary keys.
 */

function formatUuid(bytes: Uint8Array): string {
  const hex: string[] = [];
  for (const byte of bytes) {
    hex.push(byte.toString(16).padStart(2, "0"));
  }
  const s = hex.join("");
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`;
}

/** Generates a UUIDv7 string for the given time (defaults to now). */
export function uuidv7(now: number = Date.now()): string {
  const bytes = new Uint8Array(16);
  const ts = BigInt(Math.max(0, Math.floor(now)));

  // 48-bit timestamp, big-endian, in the first 6 bytes.
  bytes[0] = Number((ts >> 40n) & 0xffn);
  bytes[1] = Number((ts >> 32n) & 0xffn);
  bytes[2] = Number((ts >> 24n) & 0xffn);
  bytes[3] = Number((ts >> 16n) & 0xffn);
  bytes[4] = Number((ts >> 8n) & 0xffn);
  bytes[5] = Number(ts & 0xffn);

  // The remaining 10 bytes are random.
  crypto.getRandomValues(bytes.subarray(6));

  // Version (7) in the high nibble of byte 6.
  bytes[6] = (bytes[6] & 0x0f) | 0x70;
  // Variant (10xx) in the high bits of byte 8.
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  return formatUuid(bytes);
}
