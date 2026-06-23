import { drizzle } from "drizzle-orm/d1";
import * as schema from "./schema";

/**
 * Builds a typed Drizzle client over a D1 binding. Create one per request from
 * `env.DB`; do not cache across requests.
 */
export function getDb(d1: D1Database) {
  return drizzle(d1, { schema });
}

export type Db = ReturnType<typeof getDb>;
