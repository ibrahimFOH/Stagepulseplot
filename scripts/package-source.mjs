import { existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

const root = new URL('..', import.meta.url).pathname;
const dist = join(root, 'dist');
mkdirSync(dist, { recursive: true });

const zip = join(dist, 'StagePulseMix-source.zip');
const entries = [
  'android',
  'src',
  'public',
  'scripts',
  'tests',
  'LICENSE',
  'README.md',
  'ROADMAP.md',
  'package.json',
];

const missing = entries.filter((entry) => !existsSync(join(root, entry)));
if (missing.length) {
  throw new Error(`Missing package entries: ${missing.join(', ')}`);
}

execFileSync('zip', ['-qr', zip, ...entries], {
  cwd: root,
  stdio: 'inherit',
});

console.log(zip);
