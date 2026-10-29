import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { deflateSync } from 'node:zlib';

/**
 * Roteiro 13 (DEB-146/147/148/150) e o adendo do dono (DEB-117, DEB-145, recebimento desligado): só precondições,
 * pela API e por SQL no banco descartável. A ação sob teste é sempre pela tela.
 */
const scenarios = new Set([
  'game-fee', 'cashbox', 'uncharged-review', 'group-pix', 'advance-payment', 'group-list', 'receivables-off', 'app-update',
]);
const zone = 'America/Sao_Paulo';

/** Data local de São Paulo (AAAA-MM-DD), `days` dias a partir de hoje. */
export function localDate(days = 0) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: zone }).format(new Date(Date.now() + days * 86400000));
}

export async function seedExtra(ctx) {
  if (!scenarios.has(ctx.name)) return {};
  const dates = { today: localDate(0), yesterday: localDate(-1), tomorrow: localDate(1) };
  switch (ctx.name) {
    case 'game-fee': return { ...dates, ...(await gameFee(ctx)) };
    case 'cashbox': return dates;
    case 'uncharged-review': return { ...dates, ...(await uncharged(ctx)) };
    case 'group-pix':
    case 'receivables-off': return { ...dates, ...(await pendingWithPix(ctx)) };
    case 'advance-payment': return { ...dates, ...(await advance(ctx)) };
    case 'group-list': return { ...dates, ...(await groupList(ctx)) };
    case 'app-update': return appUpdate(ctx);
    default: return {};
  }
}

/** Jogo publicado em `days` dias às `time` (horário de São Paulo). `fee` explícito ou `noGameFee`; sem os dois, o padrão. */
export async function game(ctx, { days = 1, time = '20:00', capacity = 6, fee, noGameFee, venue = 'Quadra J1' } = {}) {
  const { api, group, owner, request } = ctx;
  const date = localDate(days);
  const startsAt = new Date(`${date}T${time}:00-03:00`);
  const body = {
    requestId: randomUUID(), title: venue, venue: { name: venue, address: 'Rua Teste 100' },
    localDate: date, localTime: time, zoneId: zone, startsAt: startsAt.toISOString(), durationMinutes: 90, capacity,
    confirmationDeadline: new Date(startsAt.getTime() - 7200000).toISOString(),
    ...(fee === undefined ? {} : { gameFeeCents: fee, useDefaultGameFee: false }),
    ...(noGameFee ? { noGameFee: true, useDefaultGameFee: false } : {}),
  };
  const created = await request(`${api}/api/groups/${ctx.gameGroup ?? group}/games`, 'POST', body, owner.token);
  const published = await request(`${api}/api/groups/${ctx.gameGroup ?? group}/games/${created.id}/publish`, 'POST',
    undefined, owner.token, { 'If-Match': `"${created.version}"` });
  assert.equal(published.status, 'PUBLISHED');
  return { ...published, body };
}

async function member(ctx, label, type) {
  const person = await ctx.user(`${ctx.name}-${label}`, ctx.runId);
  await ctx.sql(`INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at)
    VALUES ('${ctx.group}','${person.id}','ATHLETE','${type}',now(),now());`);
  return person;
}

const safe = ({ token, ...rest }) => rest;

async function confirm(ctx, gameId, person, status = 'CONFIRMED') {
  const response = await ctx.request(`${ctx.api}/api/groups/${ctx.group}/games/${gameId}/attendance`, 'PUT',
    { requestId: randomUUID(), intent: 'CONFIRM' }, person.token);
  assert.equal(response.attendance.status, status);
}

async function guest(ctx, gameId, host, displayName) {
  return ctx.request(`${ctx.api}/api/groups/${ctx.group}/games/${gameId}/attendance/guests`, 'POST',
    { requestId: randomUUID(), displayName }, host.token);
}

/** V1–V10: grupo sem valor por jogo ("não definido") e o atleta avulso. Os jogos nascem na tela (J1) e no teste (J2). */
async function gameFee({ sql, group, athlete }) {
  await sql(`BEGIN;
    UPDATE access_groups SET default_game_fee_cents=NULL, default_no_game_fee=false WHERE id='${group}';
    UPDATE group_memberships SET membership_type='AVULSO' WHERE group_id='${group}' AND user_id='${athlete.id}';
    COMMIT;`);
  return {};
}

/**
 * R1–R11: J1 Sem diária com dois avulsos e um mensalista com a convidada Bia confirmados; depois o J1 passa a R$ 20
 * (sem diária nenhuma nascer), encerra e vai para anteontem. O caixa já respondeu o saldo inicial (0).
 */
async function uncharged(ctx) {
  const { sql, request, api, group, owner, athlete, other } = ctx;
  await sql(`BEGIN;
    UPDATE access_groups SET default_game_fee_cents=NULL, default_no_game_fee=false, opening_balance_cents=0 WHERE id='${group}';
    UPDATE group_memberships SET membership_type='AVULSO' WHERE group_id='${group}' AND user_id='${athlete.id}';
    INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at)
      VALUES ('${group}','${other.id}','ATHLETE','AVULSO',now(),now());
    COMMIT;`);
  const mensal = await member(ctx, 'mensal', 'MENSALISTA');
  const j1 = await game(ctx, { noGameFee: true });
  for (const person of [athlete, other, mensal]) await confirm(ctx, j1.id, person);
  // Convidado sempre entra pela espera (VUL-239): o gestor coloca a Bia, como no jogo de verdade.
  const bia = await guest(ctx, j1.id, mensal, 'Bia');
  assert.equal(bia.status, 'WAITLISTED');
  const path = `${api}/api/groups/${group}/games/${j1.id}`;
  // "Colocar" é da Escolha manual: o grupo passa a ela só para colocar a Bia e volta à Ordem de chegada.
  await sql(`UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${group}';`);
  await request(`${path}/attendance/promote`, 'POST',
    { requestId: randomUUID(), memberId: mensal.id, guestSeq: bia.guestSeq, reason: 'Precondicao E2E' }, owner.token);
  await sql(`UPDATE access_groups SET promotion_mode='FIFO' WHERE id='${group}';`);
  const roster = await request(`${path}/attendance/roster`, 'GET', undefined, owner.token);
  assert.equal(roster.confirmed.length, 4);
  let current = await request(path, 'GET', undefined, owner.token);
  const { useDefaultGameFee, noGameFee, ...body } = j1.body;
  const edited = await request(path, 'PUT', { ...body, requestId: randomUUID(), gameFeeCents: 2000, noGameFee: false },
    owner.token, { 'If-Match': `"${current.version}"` });
  assert.equal(edited.gameFeeCents, 2000);
  current = await request(path, 'GET', undefined, owner.token);
  const completed = await request(`${path}/complete`, 'POST', undefined, owner.token, { 'If-Match': `"${current.version}"` });
  assert.equal(completed.status, 'COMPLETED');
  await sql(`UPDATE games SET starts_at=starts_at - interval '3 days', local_date=local_date - 3,
    confirmation_deadline=confirmation_deadline - interval '3 days' WHERE id='${j1.id}';`);
  const charges = await request(`${api}/api/groups/${group}/charges`, 'GET', undefined, owner.token);
  assert.equal(charges.charges.length, 0, 'Nenhuma diária nasce sozinha quando o jogo ganha valor');
  const summary = await request(`${api}/api/groups/${group}/finance/uncharged`, 'GET', undefined, owner.token);
  assert.equal(summary.peopleCount, 3);
  return { game: j1.id, gameDate: localDate(-2), mensal: safe(mensal) };
}

/** X e "recebimento desligado": avulso com uma diária de R$ 20 em aberto num grupo com chave Pix. */
async function pendingWithPix(ctx) {
  const { sql, request, api, group, owner, athlete } = ctx;
  const pixKey = `pix-${ctx.name}@e2e.saqz.test`;
  await sql(`BEGIN;
    UPDATE access_groups SET pix_key='${pixKey}', pix_label='E-mail', opening_balance_cents=0 WHERE id='${group}';
    UPDATE group_memberships SET membership_type='AVULSO' WHERE group_id='${group}' AND user_id='${athlete.id}';
    COMMIT;`);
  const j = await game(ctx, { fee: 2000 });
  await confirm(ctx, j.id, athlete);
  const charges = await request(`${api}/api/groups/${group}/charges`, 'GET', undefined, owner.token);
  assert.equal(charges.charges.length, 1);
  assert.equal(charges.charges[0].status, 'PENDING');
  return { game: j.id, charge: charges.charges[0].id, pixKey };
}

/** A1–A17: grupo com R$ 20 por jogo, chave Pix, Ordem de chegada; dois avulsos e um mensalista (leva Bia e Caio). */
async function advance(ctx) {
  const { sql, group, athlete, other } = ctx;
  const pixKey = 'pix-antecipado@e2e.saqz.test';
  await sql(`BEGIN;
    UPDATE access_groups SET pix_key='${pixKey}', pix_label='E-mail', promotion_mode='FIFO', advance_payment_required=false
      WHERE id='${group}';
    UPDATE group_memberships SET membership_type='AVULSO' WHERE group_id='${group}' AND user_id='${athlete.id}';
    INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at)
      VALUES ('${group}','${other.id}','ATHLETE','AVULSO',now(),now());
    COMMIT;`);
  const mensal = await member(ctx, 'mensal', 'MENSALISTA');
  return { pixKey, mensal: safe(mensal) };
}

/**
 * GER-003: G1 com foto e jogo sem resposta (2 de 6), G2 sem foto com "Vou" já dado, G3 sem jogo. O dono é admin dos três.
 */
async function groupList(ctx) {
  const { sql, request, api, group, secondGroup, owner, athlete } = ctx;
  const third = randomUUID();
  const png = solidPng(64, 64);
  await sql(`BEGIN;
    INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,
      default_capacity,default_game_fee_cents,mensalista_priority,auto_confirm_enabled,created_at,updated_at) VALUES
      ('${third}','${owner.id}','${randomUUID()}','Sem jogo ${ctx.name}','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',
       4,2000,false,false,now(),now());
    INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at) VALUES
      ('${third}','${owner.id}','ADMIN','AVULSO',now(),now()),
      ('${third}','${athlete.id}','ATHLETE','MENSALISTA',now(),now());
    INSERT INTO group_photos (group_id,photo_bytes,media_type,byte_size,width,height,sha256_digest,updated_by,created_at,updated_at)
      VALUES ('${group}',decode('${png.toString('hex')}','hex'),'image/png',${png.length},64,64,
        decode('${createHash('sha256').update(png).digest('hex')}','hex'),'${owner.id}',now(),now());
    COMMIT;`);
  const first = await game(ctx, { days: 2, time: '19:30', venue: 'Quadra Lista' });
  await confirm(ctx, first.id, owner);
  const second = await game({ ...ctx, gameGroup: secondGroup }, { days: 3, time: '20:00', venue: 'Praia Lista' });
  const going = await request(`${api}/api/groups/${secondGroup}/games/${second.id}/attendance`, 'PUT',
    { requestId: randomUUID(), intent: 'CONFIRM' }, athlete.token);
  assert.equal(going.attendance.status, 'CONFIRMED');
  return { thirdGroup: third, game: first.id, secondGame: second.id };
}

/** DEB-117: o dono é admin da plataforma só neste banco descartável, para mudar as versões do app pela API. */
async function appUpdate({ sql, owner }) {
  await sql(`UPDATE access_users SET platform_admin=true WHERE id='${owner.id}';`);
  return {};
}

/** PNG RGB de uma cor só (azul da marca), montado à mão para não depender de arquivo. */
function solidPng(width, height) {
  const row = Buffer.concat([Buffer.from([0]), Buffer.from(Array(width).fill([0x1f, 0x4f, 0xd8]).flat())]);
  const raw = Buffer.concat(Array(height).fill(row));
  const chunk = (type, data) => {
    const length = Buffer.alloc(4); length.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4);
  header[8] = 8; header[9] = 2;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (crc ^ 0xffffffff) >>> 0;
}
