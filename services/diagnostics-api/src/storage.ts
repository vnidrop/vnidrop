import type {
	NormalizedBugPayload,
	NormalizedCrashPayload,
	NormalizedEventsPayload,
} from "./input";

export type DiagnosticsEnv = Cloudflare.Env & {
	INGEST_KEY?: string;
	AE?: AnalyticsEngineDataset;
};

export interface StoreResult {
	id: string;
	duplicate: boolean;
	stored: number;
}

export async function storeEvents(
	payload: NormalizedEventsPayload,
	env: DiagnosticsEnv,
): Promise<StoreResult> {
	const result = await env.DB.prepare(
		`INSERT INTO event_batches (id, received_at, install_id, app_version, platform, event_count, payload_json)
		 VALUES (?, ?, ?, ?, ?, ?, ?)
		 ON CONFLICT(id) DO NOTHING`,
	)
		.bind(
			payload.batchId,
			Date.now(),
			payload.installId,
			payload.appVersion,
			payload.platform,
			payload.events.length,
			JSON.stringify(payload.events),
		)
		.run();
	const duplicate = result.meta.changes === 0;
	if (!duplicate && env.AE) {
		try {
			for (const event of payload.events) {
				env.AE.writeDataPoint({
					blobs: [
						event.name,
						payload.platform,
						payload.appVersion,
						payload.installId,
						JSON.stringify(event.properties),
						payload.batchId,
					],
					doubles: [event.timestampMillis, event.schemaVersion],
					indexes: [payload.installId],
				});
			}
		} catch (error) {
			// D1 remains the durable source of truth if the optional analytics index is unavailable.
			console.error(
				JSON.stringify({
					message: "failed to index diagnostics event batch",
					batchId: payload.batchId,
					error: error instanceof Error ? error.message : String(error),
				}),
			);
		}
	}
	return {
		id: payload.batchId,
		duplicate,
		stored: duplicate ? 0 : payload.events.length,
	};
}

export async function storeCrash(
	payload: NormalizedCrashPayload,
	env: DiagnosticsEnv,
): Promise<StoreResult & { fingerprint: string }> {
	const database = env.DB.withSession("first-primary");
	const existing = await database
		.prepare("SELECT fingerprint FROM crashes WHERE id = ?")
		.bind(payload.id)
		.first<{ fingerprint: string }>();
	if (existing) {
		return { id: payload.id, duplicate: true, stored: 0, fingerprint: existing.fingerprint };
	}

	const fingerprint = await crashFingerprint(payload.exceptionType, payload.stackTrace);
	const stackKey = payload.stackTrace
		? `crashes/${payload.id}/${crypto.randomUUID()}/stack.txt`
		: null;

	if (stackKey) {
		await env.BLOBS.put(stackKey, payload.stackTrace, {
			httpMetadata: { contentType: "text/plain; charset=utf-8" },
			customMetadata: { installId: payload.installId, fingerprint },
		});
	}

	try {
		const result = await database
			.prepare(
			`INSERT INTO crashes (
			  id, received_at, occurred_at, install_id, app_version, platform,
			  exception_type, exception_message, fingerprint, diagnostics_enabled,
			  stack_r2_key, breadcrumbs_json, schema_version
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(id) DO NOTHING`,
			)
			.bind(
				payload.id,
				Date.now(),
				payload.occurredAt,
				payload.installId,
				payload.appVersion,
				payload.platform,
				payload.exceptionType,
				payload.exceptionMessage,
				fingerprint,
				payload.diagnosticsEnabledAtCapture ? 1 : 0,
				stackKey,
				JSON.stringify(payload.breadcrumbs),
				payload.schemaVersion,
			)
			.run();
		const duplicate = result.meta.changes === 0;
		if (duplicate) {
			const stored = await database
				.prepare("SELECT fingerprint FROM crashes WHERE id = ?")
				.bind(payload.id)
				.first<{ fingerprint: string }>();
			if (!stored) throw new Error("duplicate crash row was not readable");
			if (stackKey) await deleteAttemptBlob(env, stackKey);
			return { id: payload.id, duplicate: true, stored: 0, fingerprint: stored.fingerprint };
		}
		return { id: payload.id, duplicate: false, stored: 1, fingerprint };
	} catch (error) {
		if (stackKey) {
			await deleteAttemptBlob(env, stackKey);
		}
		throw error;
	}
}

export async function storeBug(
	payload: NormalizedBugPayload,
	env: DiagnosticsEnv,
): Promise<StoreResult> {
	const database = env.DB.withSession("first-primary");
	const existing = await database
		.prepare("SELECT id FROM bugs WHERE id = ?")
		.bind(payload.id)
		.first<{ id: string }>();
	if (existing) return { id: payload.id, duplicate: true, stored: 0 };

	const logsKey = payload.logs ? `bugs/${payload.id}/${crypto.randomUUID()}/logs.txt` : null;
	if (logsKey) {
		await env.BLOBS.put(logsKey, payload.logs, {
			httpMetadata: { contentType: "text/plain; charset=utf-8" },
			customMetadata: { installId: payload.installId },
		});
	}

	try {
		const result = await database
			.prepare(
			`INSERT INTO bugs (
			  id, received_at, occurred_at, install_id, app_version, platform,
			  what_happened, expected, steps, contact, logs_r2_key,
			  device_json, breadcrumbs_json, status, schema_version
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'open', ?)
			ON CONFLICT(id) DO NOTHING`,
			)
			.bind(
				payload.id,
				Date.now(),
				payload.occurredAt,
				payload.installId,
				payload.appVersion,
				payload.platform,
				payload.whatHappened,
				payload.expected,
				payload.steps,
				payload.contact,
				logsKey,
				JSON.stringify(payload.device),
				JSON.stringify(payload.breadcrumbs),
				payload.schemaVersion,
			)
			.run();
		const duplicate = result.meta.changes === 0;
		if (duplicate && logsKey) {
			await deleteAttemptBlob(env, logsKey);
		}
		return { id: payload.id, duplicate, stored: duplicate ? 0 : 1 };
	} catch (error) {
		if (logsKey) {
			await deleteAttemptBlob(env, logsKey);
		}
		throw error;
	}
}

export async function runRetention(env: DiagnosticsEnv): Promise<void> {
	const retentionDays = boundedPositiveInt(env.RETENTION_DAYS, 90, 1, 3_650);
	const cutoff = Date.now() - retentionDays * 86_400_000;
	// Eight full passes plus the backlog check use at most 43 of D1's 50 queries per invocation.
	for (let pass = 0; pass < 8; pass += 1) {
		const hasFullBatch = await runRetentionPass(env, cutoff);
		if (!hasFullBatch) return;
	}
	const [events, crashes, bugs] = await env.DB.batch<{ count: number }>([
		env.DB.prepare("SELECT COUNT(*) AS count FROM event_batches WHERE received_at < ?").bind(
			cutoff,
		),
		env.DB.prepare("SELECT COUNT(*) AS count FROM crashes WHERE received_at < ?").bind(cutoff),
		env.DB.prepare("SELECT COUNT(*) AS count FROM bugs WHERE received_at < ?").bind(cutoff),
	]);
	console.warn(
		JSON.stringify({
			message: "diagnostics retention reached its per-run pass limit",
			cutoff,
			backlog: {
				eventBatches: events.results[0]?.count ?? 0,
				crashes: crashes.results[0]?.count ?? 0,
				bugs: bugs.results[0]?.count ?? 0,
			},
		}),
	);
}

async function runRetentionPass(env: DiagnosticsEnv, cutoff: number): Promise<boolean> {
	const reportBatchSize = 900;
	const eventBatchSize = 1_000;
	const [crashes, bugs] = await Promise.all([
		expiredBlobRows(env.DB, "crashes", "stack_r2_key", cutoff, reportBatchSize),
		expiredBlobRows(env.DB, "bugs", "logs_r2_key", cutoff, reportBatchSize),
	]);

	const blobKeys = [...crashes, ...bugs]
		.map((row) => row.blobKey)
		.filter((key): key is string => key !== null);
	for (let offset = 0; offset < blobKeys.length; offset += 1_000) {
		await env.BLOBS.delete(blobKeys.slice(offset, offset + 1_000));
	}

	const statements = [retentionStatement(env.DB, "event_batches", cutoff, eventBatchSize)];
	if (crashes.length > 0) statements.push(deleteRowsById(env.DB, "crashes", crashes));
	if (bugs.length > 0) statements.push(deleteRowsById(env.DB, "bugs", bugs));
	const [eventsResult] = await env.DB.batch(statements);
	return (
		eventsResult.meta.changes === eventBatchSize ||
		crashes.length === reportBatchSize ||
		bugs.length === reportBatchSize
	);
}

interface ExpiredBlobRow {
	id: string;
	blobKey: string | null;
}

async function expiredBlobRows(
	database: D1Database,
	table: "crashes" | "bugs",
	column: "stack_r2_key" | "logs_r2_key",
	cutoff: number,
	batchSize: number,
): Promise<ExpiredBlobRow[]> {
	const result = await database
		.prepare(
			`SELECT id, ${column} AS blobKey
			 FROM ${table}
			 WHERE received_at < ?
			 ORDER BY received_at
			 LIMIT ?`,
		)
		.bind(cutoff, batchSize)
		.all<ExpiredBlobRow>();
	return result.results;
}

function retentionStatement(
	database: D1Database,
	table: "event_batches" | "crashes" | "bugs",
	cutoff: number,
	batchSize: number,
): D1PreparedStatement {
	return database
		.prepare(
			`DELETE FROM ${table}
			 WHERE rowid IN (
			   SELECT rowid FROM ${table} WHERE received_at < ? ORDER BY received_at LIMIT ?
			 )`,
		)
		.bind(cutoff, batchSize);
}

function deleteRowsById(
	database: D1Database,
	table: "crashes" | "bugs",
	rows: ExpiredBlobRow[],
): D1PreparedStatement {
	return database
		.prepare(`DELETE FROM ${table} WHERE id IN (SELECT value FROM json_each(?))`)
		.bind(JSON.stringify(rows.map((row) => row.id)));
}

async function crashFingerprint(exceptionType: string, stackTrace: string): Promise<string> {
	const topFrames = stackTrace
		.split("\n")
		.map((line) => line.trim())
		.filter(Boolean)
		.slice(0, 4)
		.join("\n");
	const bytes = new TextEncoder().encode(`${exceptionType}\n${topFrames}`);
	const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
	return Array.from(digest, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function deleteAttemptBlob(env: DiagnosticsEnv, key: string): Promise<void> {
	try {
		await env.BLOBS.delete(key);
	} catch (error) {
		console.error(
			JSON.stringify({
				message: "failed to remove uncommitted diagnostics blob",
				error: error instanceof Error ? error.message : String(error),
			}),
		);
	}
}

function boundedPositiveInt(
	raw: string | undefined,
	fallback: number,
	minimum: number,
	maximum: number,
): number {
	const parsed = Number(raw);
	if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) return fallback;
	return parsed;
}
