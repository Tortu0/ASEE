import { cfg } from './api.js';

export async function subirImagen(file, carpeta = 'general') {
  if (!cfg.supabaseUrl || !cfg.supabaseKey) {
    throw new Error('Falta configurar SUPABASE_URL y SUPABASE_ANON_KEY en el archivo .env');
  }
  const limpio = file.name.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9._-]+/g, '-');
  const ruta = `${carpeta}/${Date.now()}-${limpio}`;
  const res = await fetch(`${cfg.supabaseUrl}/storage/v1/object/club-media/${ruta}`, {
    method: 'POST',
    headers: {
      apikey: cfg.supabaseKey,
      Authorization: `Bearer ${cfg.supabaseKey}`,
      'Content-Type': file.type || 'application/octet-stream',
      'x-upsert': 'true',
      'cache-control': 'max-age=31536000',
    },
    body: file,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Error ${res.status} al subir imagen a Supabase`);
  }
  return `${cfg.supabaseUrl}/storage/v1/object/public/club-media/${ruta}`;
}
