-- RUNWAY · Parte 10: amigos por código (con aceptación) y ubicación en vivo mientras entrenan
--
-- Privacidad:
--  * Nadie ve a nadie sin una amistad ACEPTADA por ambas partes.
--  * La ubicación solo existe en la tabla mientras la persona tiene un entrenamiento ACTIVO
--    (la app la borra al pausar o finalizar) y solo la ven sus amigos.
--  * La app no escribe directo en `amistades`: todo pasa por funciones que validan.

create table public.amistades (
  id bigint generated always as identity primary key,
  solicitante uuid not null references auth.users (id) on delete cascade,
  destinatario uuid not null references auth.users (id) on delete cascade,
  estado text not null default 'pendiente' check (estado in ('pendiente', 'aceptada')),
  creada_en timestamptz not null default now(),
  check (solicitante <> destinatario)
);
-- Una sola relación por pareja, sin importar quién la pidió
create unique index amistades_pareja on public.amistades (least(solicitante, destinatario), greatest(solicitante, destinatario));
create index amistades_destinatario on public.amistades (destinatario);

alter table public.amistades enable row level security;
create policy "Ver mis amistades" on public.amistades
  for select to authenticated
  using ((select auth.uid()) in (solicitante, destinatario));
revoke all on public.amistades from anon, authenticated;
grant select on public.amistades to authenticated;

-- ¿Son amigos aceptados? Vive en un esquema privado (la API no lo expone):
-- solo lo usan las políticas de seguridad de la ubicación en vivo
create schema if not exists privado;
grant usage on schema privado to authenticated;

create or replace function privado.son_amigos(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.amistades
    where estado = 'aceptada'
      and least(solicitante, destinatario) = least(a, b)
      and greatest(solicitante, destinatario) = greatest(a, b)
  );
$$;

-- Enviar solicitud con el código del amigo. Si esa persona ya me había enviado una, se acepta.
create or replace function public.solicitar_amistad(p_codigo text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  yo uuid := auth.uid();
  otro uuid;
  existente public.amistades%rowtype;
begin
  if yo is null then return 'sin_sesion'; end if;
  select id into otro from public.perfiles where codigo = upper(btrim(p_codigo));
  if otro is null then return 'no_existe'; end if;
  if otro = yo then return 'eres_tu'; end if;

  select * into existente from public.amistades
  where least(solicitante, destinatario) = least(yo, otro)
    and greatest(solicitante, destinatario) = greatest(yo, otro);

  if not found then
    insert into public.amistades (solicitante, destinatario) values (yo, otro);
    return 'enviada';
  elsif existente.estado = 'aceptada' then
    return 'ya_amigos';
  elsif existente.solicitante = yo then
    return 'ya_enviada';
  else
    update public.amistades set estado = 'aceptada' where id = existente.id;
    return 'aceptada';
  end if;
end;
$$;

-- Aceptar o rechazar una solicitud recibida
create or replace function public.responder_solicitud(p_id bigint, p_aceptar boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_aceptar then
    update public.amistades set estado = 'aceptada'
    where id = p_id and destinatario = auth.uid() and estado = 'pendiente';
  else
    delete from public.amistades
    where id = p_id and destinatario = auth.uid() and estado = 'pendiente';
  end if;
end;
$$;

-- Eliminar a un amigo o cancelar una solicitud enviada (cualquiera de los dos puede)
create or replace function public.eliminar_amistad(p_id bigint)
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.amistades where id = p_id and auth.uid() in (solicitante, destinatario);
$$;

-- Lista para la pantalla de amigos: nombre y avatar de la otra persona (perfiles es privado)
create or replace function public.mis_amigos()
returns table (amistad_id bigint, amigo_id uuid, nombre text, avatar text, estado text, la_envie_yo boolean)
language sql
stable
security definer
set search_path = ''
as $$
  select a.id,
         p.id,
         p.nombre,
         p.avatar,
         a.estado,
         a.solicitante = auth.uid()
  from public.amistades a
  join public.perfiles p
    on p.id = case when a.solicitante = auth.uid() then a.destinatario else a.solicitante end
  where auth.uid() in (a.solicitante, a.destinatario)
  order by a.estado, p.nombre;
$$;

-- Ubicación en vivo: una fila por persona que está entrenando ahora mismo
create table public.ubicaciones_en_vivo (
  usuario_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  nombre text not null,
  avatar text not null,
  lat double precision not null,
  lon double precision not null,
  distancia_m double precision not null default 0,
  inicio_en bigint not null,
  actualizada_en timestamptz not null default now()
);

-- La hora la pone el servidor (los relojes de los teléfonos pueden ir desfasados)
create or replace function public.marcar_hora_ubicacion()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.actualizada_en := now();
  return new;
end;
$$;
create trigger marcar_hora_ubicacion
  before insert or update on public.ubicaciones_en_vivo
  for each row execute function public.marcar_hora_ubicacion();

alter table public.ubicaciones_en_vivo enable row level security;
create policy "Veo la mía y la de mis amigos" on public.ubicaciones_en_vivo
  for select to authenticated
  using ((select auth.uid()) = usuario_id or privado.son_amigos((select auth.uid()), usuario_id));
create policy "Publico solo la mía" on public.ubicaciones_en_vivo
  for insert to authenticated with check ((select auth.uid()) = usuario_id);
create policy "Actualizo solo la mía" on public.ubicaciones_en_vivo
  for update to authenticated using ((select auth.uid()) = usuario_id) with check ((select auth.uid()) = usuario_id);
create policy "Borro solo la mía" on public.ubicaciones_en_vivo
  for delete to authenticated using ((select auth.uid()) = usuario_id);
revoke all on public.ubicaciones_en_vivo from anon;
grant select, insert, update, delete on public.ubicaciones_en_vivo to authenticated;

-- Tiempo real: los cambios de esta tabla se emiten por Supabase Realtime (respetando RLS)
alter publication supabase_realtime add table public.ubicaciones_en_vivo;

revoke execute on function privado.son_amigos(uuid, uuid) from public, anon;
revoke execute on function public.solicitar_amistad(text) from public, anon;
revoke execute on function public.responder_solicitud(bigint, boolean) from public, anon;
revoke execute on function public.eliminar_amistad(bigint) from public, anon;
revoke execute on function public.mis_amigos() from public, anon;
grant execute on function privado.son_amigos(uuid, uuid) to authenticated;
grant execute on function public.solicitar_amistad(text) to authenticated;
grant execute on function public.responder_solicitud(bigint, boolean) to authenticated;
grant execute on function public.eliminar_amistad(bigint) to authenticated;
grant execute on function public.mis_amigos() to authenticated;

-- Parte 14: índice sugerido por el revisor de rendimiento
create index if not exists amistades_solicitante on public.amistades (solicitante);
