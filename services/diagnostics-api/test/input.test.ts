import { describe, expect, it } from "vitest";
import {
	MAX_BREADCRUMBS_JSON_BYTES,
	MAX_DEVICE_JSON_BYTES,
	MAX_LOG_BYTES,
	normalizeBug,
	normalizeCrash,
	normalizeEvents,
	readJsonObject,
} from "../src/input";

const ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const INSTALL_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const ENCODER = new TextEncoder();

describe("readJsonObject", () => {
	it("enforces the byte limit on a chunked body without Content-Length", async () => {
		const bytes = ENCODER.encode(JSON.stringify({ value: "😀".repeat(40) }));
		const request = chunkedJsonRequest([
			bytes.subarray(0, 7),
			bytes.subarray(7, 31),
			bytes.subarray(31),
		]);

		expect(request.headers.has("content-length")).toBe(false);
		await expect(readJsonObject(request, 64)).resolves.toEqual({
			ok: false,
			status: 413,
			error: "payload_too_large",
		});
	});

	it("joins chunks before fatally decoding UTF-8", async () => {
		const bytes = ENCODER.encode(JSON.stringify({ value: "😀" }));
		const emojiStart = bytes.indexOf(0xf0);
		const request = chunkedJsonRequest([
			bytes.subarray(0, emojiStart + 2),
			bytes.subarray(emojiStart + 2),
		]);

		await expect(readJsonObject(request, bytes.byteLength)).resolves.toEqual({
			ok: true,
			value: { value: "😀" },
		});
	});

	it("rejects malformed UTF-8", async () => {
		const request = chunkedJsonRequest([
			new Uint8Array([0x7b, 0x22, 0x78, 0x22, 0x3a, 0x22, 0xc3, 0x28, 0x22, 0x7d]),
		]);

		await expect(readJsonObject(request, 100)).resolves.toEqual({
			ok: false,
			status: 400,
			error: "invalid_utf8",
		});
	});

	it("requires application/json with a UTF-8 charset", async () => {
		const missing = new Request("https://example.test/v1/events", {
			method: "POST",
			body: "{}",
		});
		const wrongCharset = chunkedJsonRequest([ENCODER.encode("{}")], "application/json; charset=utf-16");

		await expect(readJsonObject(missing, 100)).resolves.toEqual({
			ok: false,
			status: 415,
			error: "unsupported_media_type",
		});
		await expect(readJsonObject(wrongCharset, 100)).resolves.toEqual({
			ok: false,
			status: 415,
			error: "unsupported_media_type",
		});
	});

	it("validates Content-Length before reading the body", async () => {
		const invalid = chunkedJsonRequest([ENCODER.encode("{}")], "application/json", "invalid");
		const oversized = chunkedJsonRequest(
			[ENCODER.encode("{}")],
			"application/json",
			"999999999999999999999999999999999999",
		);

		await expect(readJsonObject(invalid, 100)).resolves.toEqual({
			ok: false,
			status: 400,
			error: "invalid_content_length",
		});
		await expect(readJsonObject(oversized, 100)).resolves.toEqual({
			ok: false,
			status: 413,
			error: "payload_too_large",
		});
	});

	it("rejects a non-object JSON root", async () => {
		const request = chunkedJsonRequest([ENCODER.encode("null")]);

		await expect(readJsonObject(request, 100)).resolves.toEqual({
			ok: false,
			status: 400,
			error: "invalid_body",
		});
	});
});

describe("normalizers", () => {
	it("preserves false booleans and rejects their string representation", () => {
		const crash = crashPayload(false);
		const normalizedCrash = normalizeCrash(crash);
		expect(normalizedCrash.ok).toBe(true);
		if (normalizedCrash.ok) {
			expect(normalizedCrash.value.diagnosticsEnabledAtCapture).toBe(false);
		}
		expect(normalizeCrash(crashPayload("false"))).toEqual({
			ok: false,
			status: 400,
			error: "invalid_diagnostics_enabled",
		});

		const bug = bugPayload({ include_logs: false, logs: "discard me" });
		const normalizedBug = normalizeBug(bug);
		expect(normalizedBug.ok).toBe(true);
		if (normalizedBug.ok) expect(normalizedBug.value.logs).toBe("");
		const missingConsent = normalizeBug(bugPayload({ logs: "discard me" }));
		expect(missingConsent.ok && missingConsent.value.logs).toBe("");
		expect(normalizeBug(bugPayload({ include_logs: "false" }))).toEqual({
			ok: false,
			status: 400,
			error: "invalid_include_logs",
		});
	});

	it("keeps logs, breadcrumbs, and device JSON within valid byte budgets", () => {
		const properties = Object.fromEntries(
			Array.from({ length: 12 }, (_, index) => [`key-${index}-${"\u0000".repeat(40)}`, "\u0000".repeat(128)]),
		);
		const breadcrumbs = Array.from({ length: 40 }, (_, index) => ({
			name: `crumb-${index}`,
			timestamp_millis: index,
			properties,
		}));
		const result = normalizeBug(
			bugPayload({
				include_logs: true,
				logs: "😀".repeat(60_000),
				breadcrumbs,
				device: {
					device_name: "\u0000".repeat(200),
					device_model: "\u0000".repeat(200),
					operating_system: "\u0000".repeat(300),
					network: "\u0000".repeat(150),
					battery_level: "\u0000".repeat(100),
				},
			}),
		);

		expect(result.ok).toBe(true);
		if (!result.ok) return;
		const breadcrumbsJson = JSON.stringify(result.value.breadcrumbs);
		const deviceJson = JSON.stringify(result.value.device);
		expect(ENCODER.encode(result.value.logs).byteLength).toBe(MAX_LOG_BYTES);
		expect(ENCODER.encode(breadcrumbsJson).byteLength).toBeLessThanOrEqual(
			MAX_BREADCRUMBS_JSON_BYTES,
		);
		expect(ENCODER.encode(deviceJson).byteLength).toBeLessThanOrEqual(MAX_DEVICE_JSON_BYTES);
		expect(JSON.parse(breadcrumbsJson)).toEqual(result.value.breadcrumbs);
		expect(JSON.parse(deviceJson)).toEqual(result.value.device);
	});

	it("requires stable report IDs and validates supplied IDs and schema versions", () => {
		const result = normalizeEvents({
			events: [{ name: "opened", ts: 1, schema_version: 1 }],
		});
		expect(result).toEqual({ ok: false, status: 400, error: "invalid_batch_id" });
		const legacyInstall = normalizeEvents({
			batch_id: ID,
			install_id: "legacy-test-install",
			events: [{ name: "opened", ts: 1 }],
		});
		expect(legacyInstall.ok && legacyInstall.value.installId).toBe("legacy-test-install");
		const missingInstall = normalizeEvents({
			batch_id: ID,
			events: [{ name: "opened", ts: 1 }],
		});
		expect(missingInstall.ok && missingInstall.value.installId).toBe("unknown");
		expect(
			normalizeEvents({
				batch_id: ID,
				install_id: "bad\u0000install",
				events: [{ name: "opened", ts: 1 }],
			}),
		).toEqual({ ok: false, status: 400, error: "invalid_install_id" });

		expect(
			normalizeEvents({
				batch_id: "not-a-uuid",
				events: [{ name: "opened", timestamp_millis: 1 }],
			}),
		).toEqual({ ok: false, status: 400, error: "invalid_batch_id" });
		expect(
			normalizeEvents({
				batch_id: ID,
				install_id: INSTALL_ID,
				events: [{ name: "opened", timestamp_millis: 1, schema_version: 2 }],
			}),
		).toEqual({ ok: false, status: 400, error: "unsupported_schema_version" });
	});
});

function chunkedJsonRequest(
	chunks: readonly Uint8Array[],
	contentType = "application/json; charset=utf-8",
	contentLength?: string,
): Request {
	return new Request("https://example.test/v1/events", {
		method: "POST",
		headers: {
			"content-type": contentType,
			...(contentLength == null ? {} : { "content-length": contentLength }),
		},
		body: new ReadableStream<Uint8Array>({
			start(controller) {
				for (const chunk of chunks) controller.enqueue(chunk);
				controller.close();
			},
		}),
	});
}

function crashPayload(diagnosticsEnabled: unknown): Record<string, unknown> {
	return {
		id: ID,
		install_id: INSTALL_ID,
		app_version: "1.0",
		platform: "test",
		exception_type: "ExampleError",
		exception_message: "message",
		stack_trace: "stack",
		occurred_at: 1,
		diagnostics_enabled: diagnosticsEnabled,
		schema_version: 1,
		breadcrumbs: [],
	};
}

function bugPayload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		id: ID,
		install_id: INSTALL_ID,
		app_version: "1.0",
		platform: "test",
		occurred_at: 1,
		what_happened: "It failed",
		expected: "It worked",
		steps: "Open the app",
		contact: "",
		logs: "",
		device: {},
		breadcrumbs: [],
		schema_version: 1,
		...overrides,
	};
}
