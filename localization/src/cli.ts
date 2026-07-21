#!/usr/bin/env bun
import { migrate } from "./commands/migrate";
import { generate } from "./commands/generate";
import { validate } from "./commands/validate";

const [cmd] = process.argv.slice(2);

switch (cmd) {
  case "migrate":
    await migrate();
    break;
  case "generate":
    await generate();
    break;
  case "validate":
    await validate();
    break;
  default:
    console.error(`Unknown command: ${cmd ?? "(none)"}\nUsage: loc <migrate|generate|validate>`);
    process.exit(1);
}
