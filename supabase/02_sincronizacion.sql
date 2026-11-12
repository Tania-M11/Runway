-- RUNWAY · Parte 9: copia remota de rutas y entrenamientos (sincronización diferida)
-- El teléfono es la fuente principal (offline-first); aquí se guarda una copia por cuenta.
-- Los id son UUID creados en el teléfono, así una misma fila nunca se duplica al reintentar.
-- Las fechas van en milisegundos (igual que en SQLite) para no convertir zonas horarias.

create table public.rutas (
  id uuid primary key,
  usuario_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  nombre text not null check (char_length(nombre) between 1 and 40),
  visibilidad text not null check (visibilidad in ('Privada', 'Reutilizable', 'Compartida')),
  modalidad text not null check (modalidad in ('Circular', 'Lineal')),
  distancia_km double precision not null,
  subida_m double precision not null,
  bajada_m double precision not null,
  trazado text not null,                -- polyline6
  elevaciones text not null,            -- altitudes separadas por ';'
  intervalo_elevacion_m double precision not null,
  calles text not null,                 -- separadas por '|'
  giros integer not null,
  inicio_nombre text not null,
  fin_nombre text,
  creada_en bigint not null,
  actualizada_en bigint not null,
  eliminada boolean not null default false
);

create table public.entrenamientos (
  id uuid primary key,
  usuario_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  ruta_id uuid,
  ruta_nombre text,
  ruta_trazado text,
  inicio_en bigint not null,
  fin_en bigint,
  distancia_m double precision not null,
  duracion_ms bigint not null,
  subida_m double precision not null,
  segmentos text not null,              -- un polyline6 por tramo, separados por '|'
  altitudes text not null,              -- separadas por ';'
  eliminado boolean not null default false
);

create index rutas_usuario on public.rutas (usuario_id);
create index entrenamientos_usuario on public.entrenamientos (usuario_id, inicio_en desc);

-- Seguridad por filas: cada cuenta solo lee y escribe lo suyo
alter table public.rutas enable row level security;
alter table public.entrenamientos enable row level security;

create policy "Rutas propias" on public.rutas
  for all to authenticated
  using ((select auth.uid()) = usuario_id)
  with check ((select auth.uid()) = usuario_id);

create policy "Entrenamientos propios" on public.entrenamientos
  for all to authenticated
  using ((select auth.uid()) = usuario_id)
  with check ((select auth.uid()) = usuario_id);

revoke all on public.rutas, public.entrenamientos from anon;
grant select, insert, update, delete on public.rutas, public.entrenamientos to authenticated;
