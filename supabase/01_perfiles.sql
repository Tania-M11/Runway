-- RUNWAY · Parte 8: perfiles de usuario
-- Cada cuenta de Supabase Auth tiene un perfil con nombre, avatar, código de amigo y preferencias.

-- Código de amigo: 6 caracteres sin confusiones (sin 0/O, 1/I)
create or replace function public.generar_codigo_amigo()
returns text
language plpgsql
volatile
set search_path = ''
as $$
declare
  alfabeto constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  nuevo text;
begin
  loop
    nuevo := '';
    for i in 1..6 loop
      nuevo := nuevo || substr(alfabeto, 1 + floor(random() * length(alfabeto))::int, 1);
    end loop;
    exit when not exists (select 1 from public.perfiles p where p.codigo = nuevo);
  end loop;
  return nuevo;
end;
$$;

create table public.perfiles (
  id uuid primary key references auth.users (id) on delete cascade,
  nombre text not null check (char_length(btrim(nombre)) between 1 and 30),
  avatar text not null default 'avatar_1'
    check (avatar in ('avatar_1', 'avatar_2', 'avatar_3', 'avatar_4', 'avatar_5')),
  codigo text not null unique default public.generar_codigo_amigo(),
  nivel text check (nivel in ('Principiante', 'Intermedio', 'Avanzado')),
  desnivel text check (desnivel in ('Plano', 'Moderado', 'Exigente')),
  creado_en timestamptz not null default now()
);

-- Seguridad por filas: cada quien solo ve y edita su propio perfil
-- (los amigos podrán ver nombre y avatar mediante funciones en la parte 10)
alter table public.perfiles enable row level security;

create policy "Cada quien ve su perfil" on public.perfiles
  for select to authenticated using ((select auth.uid()) = id);

create policy "Cada quien edita su perfil" on public.perfiles
  for update to authenticated
  using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

-- La app solo puede cambiar estas columnas (ni el id ni el código)
revoke all on public.perfiles from anon, authenticated;
grant select on public.perfiles to authenticated;
grant update (nombre, avatar, nivel, desnivel) on public.perfiles to authenticated;

-- Al registrarse, el perfil se crea solo con el nombre y el avatar del formulario
create or replace function public.al_crear_usuario()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.perfiles (id, nombre, avatar)
  values (
    new.id,
    left(coalesce(nullif(btrim(new.raw_user_meta_data ->> 'nombre'), ''), 'Corredor'), 30),
    case
      when new.raw_user_meta_data ->> 'avatar' in ('avatar_1', 'avatar_2', 'avatar_3', 'avatar_4', 'avatar_5')
        then new.raw_user_meta_data ->> 'avatar'
      else 'avatar_1'
    end
  );
  return new;
end;
$$;

create trigger al_crear_usuario
  after insert on auth.users
  for each row execute function public.al_crear_usuario();

revoke execute on function public.al_crear_usuario() from public, anon, authenticated;
revoke execute on function public.generar_codigo_amigo() from public, anon, authenticated;
