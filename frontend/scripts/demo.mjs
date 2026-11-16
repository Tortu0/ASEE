// Arranca el mock de los 6 servicios + el gateway/frontend con un secreto de demostración.
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const env = { ...process.env, JWT_SECRET: process.env.JWT_SECRET || 'club-demo-secret-club-demo-secret-0001' };
const run = f => { const p = spawn(process.execPath, [resolve(root, f)], { env, stdio: 'inherit' }); p.on('exit', c => process.exit(c ?? 0)); return p; };
run('mock/server.js'); setTimeout(() => run('gateway/server.js'), 600);
process.on('SIGINT', () => process.exit(0));
