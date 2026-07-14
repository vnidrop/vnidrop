import { env, exports } from "cloudflare:workers";
import { createExecutionContext } from "cloudflare:test";
import { describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import type {
	NormalizedBugPayload,
	NormalizedCrashPayload,
	NormalizedEventsPayload,
} from "../src/input";
import {
	type DiagnosticsEnv,
	runRetention,
	storeBug,
	storeCrash,
	storeEvents,
} from "../src/storage";

const INSTALL_ID = "10000000-0000-4000-8000-000000000000";

describe("diagnostics Worker", () => {
	it("keeps public routing narrow and fails closed", async () => {
		const live = await exports.default.fetch(new Request("https://diagnostics.test/live"));
		expect(live.status).toBe(200);
		expect(await live.json()).toMatchObject({ ok: true, schema: 1 });

		const health = await exports.default.fetch(healthRequest());
		expect(health.status).toBe(200);
		expect(await health.json()).toMatchObject({ ok: true });
		const unauthorizedHealth = await exports.default.fetch(healthRequest("wrong-key"));
		expect(unauthorizedHealth.status).toBe(401);

		const unknown = await exports.default.fetch(
			new Request("https://diagnostics.test/v1/not-real", { method: "POST" }),
		);
		expect(unknown.status).toBe(404);

		const unauthorized = await exports.default.fetch(
			jsonRequest("/v1/events", eventPayload(uuid(1)), "wrong-key"),
		);
		expect(unauthorized.status).toBe(401);
		expect(await unauthorized.json()).toEqual({ error: "unauthorized" });
		expect(unauthorized.headers.get("x-request-id")).toMatch(
			/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/,
		);

		const preflight = await exports.default.fetch(
			new Request("https://diagnostics.test/v1/events", { method: "OPTIONS" }),
		);
		expect(preflight.status).toBe(204);
		expect(preflight.headers.get("access-control-allow-origin")).toBeNull();
		expect(preflight.headers.get("allow")).toBe("POST, OPTIONS");
	});

	it("applies the source limit before shared-key verification", async () => {
		let installLimitCalls = 0;
		const limitedEnv: DiagnosticsEnv = {
			...env,
			SOURCE_RATE_LIMITER: {
				limit: async () => ({ success: false }),
			} as RateLimit,
			INSTALL_RATE_LIMITER: {
				limit: async () => {
					installLimitCalls += 1;
					return { success: true };
				},
			} as RateLimit,
		};
		const context = createExecutionContext();

		const response = await worker.fetch(
			jsonRequest("/v1/events", eventPayload(uuid(3)), "wrong-key", "198.51.100.3"),
			limitedEnv,
			context,
		);

		expect(response.status).toBe(429);
		expect(response.headers.get("retry-after")).toBe("60");
		expect(await response.json()).toEqual({ error: "rate_limited" });
		expect(installLimitCalls).toBe(0);
	});

	it("returns structured errors for invalid bodies and asynchronous storage failures", async () => {
		const invalid = await exports.default.fetch(jsonRequest("/v1/events", null));
		expect(invalid.status).toBe(400);
		expect(await invalid.json()).toEqual({ error: "invalid_body" });

		const rejection = new Error("simulated D1 rejection");
		const statement = {
			bind: () => statement,
			first: async () => Promise.reject(rejection),
			run: async () => Promise.reject(rejection),
		};
		const rejectingDatabase = {
			prepare: () => statement,
		} as unknown as D1Database;
		const rejectingEnv: DiagnosticsEnv = { ...env, DB: rejectingDatabase };

		const healthContext = createExecutionContext();
		const unhealthy = await worker.fetch(
			healthRequest(env.INGEST_KEY, "198.51.100.4"),
			rejectingEnv,
			healthContext,
		);
		expect(unhealthy.status).toBe(503);
		expect(await unhealthy.json()).toEqual({ ok: false, error: "dependency_unavailable" });

		const ingestContext = createExecutionContext();
		const failedIngest = await worker.fetch(
			jsonRequest("/v1/events", eventPayload(uuid(2)), env.INGEST_KEY, "198.51.100.2"),
			rejectingEnv,
			ingestContext,
		);
		expect(failedIngest.status).toBe(500);
		expect(await failedIngest.json()).toEqual({ error: "internal" });
	});

	it("deduplicates event batches using the client batch ID", async () => {
		const id = uuid(10);
		const first = await exports.default.fetch(jsonRequest("/v1/events", eventPayload(id)));
		const second = await exports.default.fetch(jsonRequest("/v1/events", eventPayload(id)));

		expect(first.status).toBe(202);
		expect(await first.json()).toMatchObject({
			ok: true,
			id,
			stored: 1,
			duplicate: false,
		});
		expect(second.status).toBe(202);
		expect(await second.json()).toMatchObject({
			ok: true,
			id,
			stored: 0,
			duplicate: true,
		});

		const row = await env.DB.prepare(
			"SELECT event_count AS eventCount, payload_json AS payloadJson FROM event_batches WHERE id = ?",
		)
			.bind(id)
			.first<{ eventCount: number; payloadJson: string }>();
		expect(row?.eventCount).toBe(1);
		expect(JSON.parse(row?.payloadJson ?? "null")).toEqual([
			{
				name: "app_open",
				timestampMillis: 1,
				properties: { screen: "home" },
				schemaVersion: 1,
			},
		]);
	});

	it("keeps D1 idempotency when the optional analytics index is enabled", async () => {
		const points: AnalyticsEngineDataPoint[] = [];
		const analytics = {
			writeDataPoint: (point: AnalyticsEngineDataPoint) => points.push(point),
		} as AnalyticsEngineDataset;
		const analyticsEnv: DiagnosticsEnv = { ...env, AE: analytics };
		const payload: NormalizedEventsPayload = {
			batchId: uuid(11),
			installId: INSTALL_ID,
			appVersion: "1.0",
			platform: "test",
			events: [
				{
					name: "indexed",
					timestampMillis: 1,
					properties: {},
					schemaVersion: 1,
				},
			],
		};

		expect(await storeEvents(payload, analyticsEnv)).toMatchObject({ duplicate: false, stored: 1 });
		expect(await storeEvents(payload, analyticsEnv)).toMatchObject({ duplicate: true, stored: 0 });
		expect(points).toHaveLength(1);
	});

	it("keeps the accepted crash blob when a duplicate request arrives", async () => {
		const id = uuid(20);
		const first = await exports.default.fetch(
			jsonRequest("/v1/crashes", crashPayload(id, "first stack")),
		);
		const second = await exports.default.fetch(
			jsonRequest("/v1/crashes", crashPayload(id, "second stack")),
		);

		expect(first.status).toBe(202);
		const firstBody = await first.json<{ fingerprint: string }>();
		expect(firstBody).toMatchObject({ ok: true, id, duplicate: false });
		expect(second.status).toBe(202);
		const secondBody = await second.json<{ fingerprint: string }>();
		expect(secondBody).toMatchObject({ ok: true, id, duplicate: true });

		const row = await env.DB.prepare(
			`SELECT stack_r2_key AS stackKey, breadcrumbs_json AS breadcrumbsJson,
			        fingerprint
			 FROM crashes WHERE id = ?`,
		)
			.bind(id)
			.first<{ stackKey: string; breadcrumbsJson: string; fingerprint: string }>();
		expect(row?.stackKey).toMatch(new RegExp(`^crashes/${id}/[0-9a-f-]+/stack\\.txt$`));
		expect(firstBody.fingerprint).toBe(row?.fingerprint);
		expect(secondBody.fingerprint).toBe(row?.fingerprint);
		expect(JSON.parse(row?.breadcrumbsJson ?? "null")).toEqual([]);
		expect(await (await env.BLOBS.get(row?.stackKey ?? "missing"))?.text()).toBe("first stack");

		const objects = await env.BLOBS.list({ prefix: `crashes/${id}/` });
		expect(objects.objects.map((object) => object.key)).toEqual([row?.stackKey]);
	});

	it("stores bug metadata as JSON and cleans the duplicate upload attempt", async () => {
		const id = uuid(30);
		const payload = bugPayload(id, "first logs");
		const first = await exports.default.fetch(jsonRequest("/v1/bugs", payload));
		const second = await exports.default.fetch(
			jsonRequest("/v1/bugs", bugPayload(id, "second logs")),
		);

		expect(first.status).toBe(202);
		expect(await first.json()).toMatchObject({ ok: true, id, duplicate: false });
		expect(second.status).toBe(202);
		expect(await second.json()).toMatchObject({ ok: true, id, duplicate: true });

		const row = await env.DB.prepare(
			`SELECT occurred_at AS occurredAt, logs_r2_key AS logsKey,
			        device_json AS deviceJson, breadcrumbs_json AS breadcrumbsJson
			 FROM bugs WHERE id = ?`,
		)
			.bind(id)
			.first<{
				occurredAt: number;
				logsKey: string;
				deviceJson: string;
				breadcrumbsJson: string;
			}>();
		expect(row?.occurredAt).toBe(3);
		expect(JSON.parse(row?.deviceJson ?? "null")).toEqual({
			deviceName: "Test device",
			deviceModel: "Model",
			operatingSystem: "Test OS",
			network: "offline",
			batteryLevel: "90%",
		});
		expect(JSON.parse(row?.breadcrumbsJson ?? "null")).toEqual([
			{ name: "opened", timestampMillis: 2, properties: {} },
		]);
		expect(await (await env.BLOBS.get(row?.logsKey ?? "missing"))?.text()).toBe("first logs");

		const objects = await env.BLOBS.list({ prefix: `bugs/${id}/` });
		expect(objects.objects.map((object) => object.key)).toEqual([row?.logsKey]);
	});

	it("acknowledges known report IDs without touching an unavailable blob store", async () => {
		const crash = normalizedCrash(uuid(31), "accepted stack");
		const bug = normalizedBug(uuid(32), "accepted logs");
		const firstCrash = await storeCrash(crash, env);
		await storeBug(bug, env);
		let blobWrites = 0;
		const unavailableBlobs = {
			put: async () => {
				blobWrites += 1;
				throw new Error("simulated R2 outage");
			},
		} as unknown as R2Bucket;
		const unavailableEnv: DiagnosticsEnv = { ...env, BLOBS: unavailableBlobs };

		await expect(
			storeCrash({ ...crash, stackTrace: "retry stack" }, unavailableEnv),
		).resolves.toEqual({
			id: crash.id,
			duplicate: true,
			stored: 0,
			fingerprint: firstCrash.fingerprint,
		});
		await expect(
			storeBug({ ...bug, logs: "retry logs" }, unavailableEnv),
		).resolves.toEqual({ id: bug.id, duplicate: true, stored: 0 });
		expect(blobWrites).toBe(0);
	});

	it("removes uploaded report blobs when D1 rejects the metadata write", async () => {
		const rejection = new Error("simulated D1 write rejection");
		const rejectingDatabase = databaseWithSession(
			() => null,
			async () => Promise.reject(rejection),
		);
		const rejectingEnv: DiagnosticsEnv = { ...env, DB: rejectingDatabase };
		const crashId = uuid(33);
		const bugId = uuid(34);

		const crashResponse = await worker.fetch(
			jsonRequest("/v1/crashes", crashPayload(crashId, "orphan candidate"), env.INGEST_KEY, "198.51.100.33"),
			rejectingEnv,
			createExecutionContext(),
		);
		const bugResponse = await worker.fetch(
			jsonRequest("/v1/bugs", bugPayload(bugId, "orphan candidate"), env.INGEST_KEY, "198.51.100.34"),
			rejectingEnv,
			createExecutionContext(),
		);

		expect(crashResponse.status).toBe(500);
		expect(await crashResponse.json()).toEqual({ error: "internal" });
		expect(bugResponse.status).toBe(500);
		expect(await bugResponse.json()).toEqual({ error: "internal" });
		expect((await env.BLOBS.list({ prefix: `crashes/${crashId}/` })).objects).toEqual([]);
		expect((await env.BLOBS.list({ prefix: `bugs/${bugId}/` })).objects).toEqual([]);
	});

	it("removes expired rows and their exact R2 objects while preserving current data", async () => {
		const oldEventId = uuid(40);
		const oldCrashId = uuid(41);
		const oldBugId = uuid(42);
		const currentEventId = uuid(43);
		const oldCrashKey = `crashes/${oldCrashId}/retention/stack.txt`;
		const oldBugKey = `bugs/${oldBugId}/retention/logs.txt`;
		const oldReceivedAt = Date.now() - 100 * 86_400_000;

		await Promise.all([
			env.BLOBS.put(oldCrashKey, "expired crash"),
			env.BLOBS.put(oldBugKey, "expired logs"),
		]);
		await env.DB.batch([
			env.DB.prepare(
				`INSERT INTO event_batches
				 (id, received_at, install_id, app_version, platform, event_count, payload_json)
				 VALUES (?, ?, ?, '', '', 1, '[]')`,
			).bind(oldEventId, oldReceivedAt, INSTALL_ID),
			env.DB.prepare(
				`INSERT INTO event_batches
				 (id, received_at, install_id, app_version, platform, event_count, payload_json)
				 VALUES (?, ?, ?, '', '', 1, '[]')`,
			).bind(currentEventId, Date.now(), INSTALL_ID),
			env.DB.prepare(
				`INSERT INTO crashes
				 (id, received_at, occurred_at, install_id, app_version, platform,
				  exception_type, exception_message, fingerprint, diagnostics_enabled,
				  stack_r2_key, breadcrumbs_json, schema_version)
				 VALUES (?, ?, ?, ?, '', '', 'Error', '', 'fingerprint', 1, ?, '[]', 1)`,
			).bind(oldCrashId, oldReceivedAt, oldReceivedAt, INSTALL_ID, oldCrashKey),
			env.DB.prepare(
				`INSERT INTO bugs
				 (id, received_at, occurred_at, install_id, app_version, platform,
				  what_happened, expected, steps, contact, logs_r2_key,
				  device_json, breadcrumbs_json, status, schema_version)
				 VALUES (?, ?, ?, ?, '', '', 'failed', 'worked', '', '', ?, '{}', '[]', 'open', 1)`,
			).bind(oldBugId, oldReceivedAt, oldReceivedAt, INSTALL_ID, oldBugKey),
		]);

		await runRetention(env);

		for (const [table, id] of [
			["event_batches", oldEventId],
			["crashes", oldCrashId],
			["bugs", oldBugId],
		] as const) {
			const row = await env.DB.prepare(`SELECT id FROM ${table} WHERE id = ?`).bind(id).first();
			expect(row).toBeNull();
		}
		expect(await env.BLOBS.head(oldCrashKey)).toBeNull();
		expect(await env.BLOBS.head(oldBugKey)).toBeNull();
		expect(
			await env.DB.prepare("SELECT id FROM event_batches WHERE id = ?").bind(currentEventId).first(),
		).not.toBeNull();
	});

	it("bounds a full retention run below the D1 per-invocation query limit", async () => {
		let queryCount = 0;
		let batchCalls = 0;
		const blobDeleteBatchSizes: number[] = [];
		const rows = Array.from({ length: 900 }, (_, index) => ({
			id: `expired-${index}`,
			blobKey: `expired/${index}`,
		}));
		const database = {
			prepare: () => {
				const statement = {
					bind: () => statement,
					all: async () => {
						queryCount += 1;
						return d1Result(rows, 0);
					},
				};
				return statement;
			},
			batch: async (statements: D1PreparedStatement[]) => {
				batchCalls += 1;
				queryCount += statements.length;
				if (batchCalls === 9) {
					return statements.map(() => d1Result([{ count: 1 }], 0));
				}
				return statements.map((_, index) => d1Result([], index === 0 ? 1_000 : 900));
			},
		} as unknown as D1Database;
		const warning = vi.spyOn(console, "warn").mockImplementation(() => undefined);
		const blobs = {
			delete: async (keys: string | string[]) => {
				blobDeleteBatchSizes.push(typeof keys === "string" ? 1 : keys.length);
			},
		} as unknown as R2Bucket;

		try {
			await runRetention({ ...env, DB: database, BLOBS: blobs });
		} finally {
			warning.mockRestore();
		}

		expect(queryCount).toBe(43);
		expect(blobDeleteBatchSizes).toHaveLength(16);
		expect(Math.max(...blobDeleteBatchSizes)).toBe(1_000);
	});

	it("converges an expired report backlog across bounded retention runs", async () => {
		const oldReceivedAt = Date.now() - 100 * 86_400_000;
		await env.DB.prepare(
			`WITH digits(value) AS (
			   VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9)
			 ), sequence(value) AS (
			   SELECT thousands.value * 1000 + hundreds.value * 100 + tens.value * 10 + ones.value + 1
			   FROM digits AS thousands
			   CROSS JOIN digits AS hundreds
			   CROSS JOIN digits AS tens
			   CROSS JOIN digits AS ones
			   WHERE thousands.value * 1000 + hundreds.value * 100 + tens.value * 10 + ones.value < 7201
			 )
			 INSERT INTO crashes (
			   id, received_at, occurred_at, install_id, app_version, platform,
			   exception_type, exception_message, fingerprint, diagnostics_enabled,
			   stack_r2_key, breadcrumbs_json, schema_version
			 )
			 SELECT 'retention-backlog-' || printf('%04d', value), ?, ?, ?, '', '',
			        'Error', '', 'fingerprint-' || value, 0, NULL, '[]', 1
			 FROM sequence`,
		)
			.bind(oldReceivedAt, oldReceivedAt, INSTALL_ID)
			.run();
		const warning = vi.spyOn(console, "warn").mockImplementation(() => undefined);

		try {
			await runRetention(env);
			const afterFirstRun = await env.DB.prepare(
				"SELECT COUNT(*) AS count FROM crashes WHERE id LIKE 'retention-backlog-%'",
			).first<{ count: number }>();
			expect(afterFirstRun?.count).toBe(1);

			await runRetention(env);
			const afterSecondRun = await env.DB.prepare(
				"SELECT COUNT(*) AS count FROM crashes WHERE id LIKE 'retention-backlog-%'",
			).first<{ count: number }>();
			expect(afterSecondRun?.count).toBe(0);
		} finally {
			warning.mockRestore();
		}
	});
});

function jsonRequest(
	path: string,
	body: unknown,
	key = env.INGEST_KEY,
	source = "198.51.100.1",
): Request {
	return new Request(`https://diagnostics.test${path}`, {
		method: "POST",
		headers: {
			"content-type": "application/json; charset=utf-8",
			"cf-connecting-ip": source,
			"x-vnidrop-install-id": INSTALL_ID,
			"x-vnidrop-key": key,
		},
		body: JSON.stringify(body),
	});
}

function healthRequest(key = env.INGEST_KEY, source = "198.51.100.1"): Request {
	return new Request("https://diagnostics.test/health", {
		headers: {
			"cf-connecting-ip": source,
			"x-vnidrop-key": key,
		},
	});
}

function eventPayload(batchId: string): Record<string, unknown> {
	return {
		batchId,
		installId: INSTALL_ID,
		appVersion: "1.0",
		platform: "test",
		events: [
			{
				name: "app_open",
				timestampMillis: 1,
				properties: { screen: "home" },
				schemaVersion: 1,
			},
		],
	};
}

function crashPayload(id: string, stackTrace: string): Record<string, unknown> {
	return {
		id,
		installId: INSTALL_ID,
		appVersion: "1.0",
		platform: "test",
		exceptionType: "TestError",
		exceptionMessage: "failed",
		stackTrace,
		timestampMillis: 2,
		diagnosticsEnabledAtCapture: true,
		breadcrumbs: [],
		schemaVersion: 1,
	};
}

function bugPayload(id: string, logs: string): Record<string, unknown> {
	return {
		id,
		installId: INSTALL_ID,
		appVersion: "1.0",
		platform: "test",
		timestampMillis: 3,
		whatHappened: "It failed",
		expected: "It should work",
		steps: "Open the app",
		contact: "",
		includeLogs: true,
		logs,
		device: {
			deviceName: "Test device",
			deviceModel: "Model",
			operatingSystem: "Test OS",
			network: "offline",
			batteryLevel: "90%",
		},
		breadcrumbs: [{ name: "opened", timestampMillis: 2, properties: {} }],
		schemaVersion: 1,
	};
}

function normalizedCrash(id: string, stackTrace: string): NormalizedCrashPayload {
	return {
		id,
		installId: INSTALL_ID,
		appVersion: "1.0",
		platform: "test",
		exceptionType: "TestError",
		exceptionMessage: "failed",
		stackTrace,
		occurredAt: 2,
		diagnosticsEnabledAtCapture: true,
		breadcrumbs: [],
		schemaVersion: 1,
	};
}

function normalizedBug(id: string, logs: string): NormalizedBugPayload {
	return {
		id,
		installId: INSTALL_ID,
		appVersion: "1.0",
		platform: "test",
		occurredAt: 3,
		whatHappened: "It failed",
		expected: "It should work",
		steps: "Open the app",
		contact: "",
		logs,
		device: {
			deviceName: "Test device",
			deviceModel: "Model",
			operatingSystem: "Test OS",
			network: "offline",
			batteryLevel: "90%",
		},
		breadcrumbs: [],
		schemaVersion: 1,
	};
}

function databaseWithSession(
	first: () => unknown,
	run: () => Promise<D1Result>,
): D1Database {
	const statement = {
		bind: () => statement,
		first: async () => first(),
		run,
	} as unknown as D1PreparedStatement;
	const session = {
		prepare: () => statement,
	} as unknown as D1DatabaseSession;
	return {
		withSession: () => session,
	} as unknown as D1Database;
}

function d1Result<T>(results: T[], changes: number): D1Result<T> {
	return { success: true, results, meta: { changes } } as D1Result<T>;
}

function uuid(suffix: number): string {
	return `00000000-0000-4000-8000-${suffix.toString().padStart(12, "0")}`;
}
