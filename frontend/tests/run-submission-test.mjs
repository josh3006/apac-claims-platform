import { build } from 'esbuild';
import { mkdir } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

// esbuild is also used by Angular's builder; this test needs no browser or test framework.
await mkdir('.angular', { recursive: true });
const outfile = resolve('.angular/submission-retry.test.mjs');
await build({ entryPoints: ['tests/submission-retry.test.ts'], bundle: true, platform: 'node', format: 'esm', packages: 'external', outfile });
await import(pathToFileURL(outfile).href);
