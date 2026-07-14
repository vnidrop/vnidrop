import type { D1Migration } from "@cloudflare/vitest-pool-workers";

declare global {
	namespace Cloudflare {
		interface Env {
			INGEST_KEY: string;
			TEST_MIGRATIONS: D1Migration[];
		}
	}
}

export {};
