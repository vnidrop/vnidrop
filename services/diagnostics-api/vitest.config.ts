import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-pool-workers";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const migrationsPath = resolve(dirname(fileURLToPath(import.meta.url)), "migrations");

export default defineConfig({
	plugins: [
		cloudflareTest(async () => ({
			wrangler: { configPath: "./wrangler.jsonc" },
			miniflare: {
				bindings: {
					INGEST_KEY: "test-ingest-key",
					TEST_MIGRATIONS: await readD1Migrations(migrationsPath),
				},
			},
		})),
	],
	test: {
		setupFiles: ["./test/apply-migrations.ts"],
	},
});
