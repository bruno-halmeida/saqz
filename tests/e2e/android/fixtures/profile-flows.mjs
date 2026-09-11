import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const scenarios = new Set(['sports-profile', 'member-privacy', 'monthly-history']);

/** Preconditions only, against this run's real API/auth/database; never exports credentials. */
export async function seedExtra(ctx) {
  if (!scenarios.has(ctx.name)) return {};
  const { sql, group, other } = ctx;
  await sql(`INSERT INTO group_memberships
    (group_id,user_id,role,membership_type,created_at,updated_at)
    VALUES ('${group}','${other.id}','ATHLETE','MENSALISTA',now(),now());`);
  if (ctx.name === 'monthly-history') return monthlyHistory(ctx);
  await sportsProfiles(ctx);
  if (ctx.name === 'sports-profile') return {};
  return memberPrivacy(ctx);
}

async function sportsProfiles({ request, api, group, secondGroup, athlete, other }) {
  const profiles = [
    [group, athlete, { nickname: 'Atleta Quadra', position: 'PONTA', level: 'INTERMEDIARIO', heightCm: 181 }],
    [secondGroup, athlete, { nickname: 'Atleta Praia', preferredSide: 'ESQUERDA', level: 'INICIANTE' }],
    [group, other, {
      nickname: 'Par Reservado', position: 'LEVANTADOR', secondaryPosition: 'LIBERO', level: 'AVANCADO', heightCm: 198,
    }],
  ];
  for (const [id, person, attributes] of profiles) {
    const saved = await request(`${api}/api/groups/${id}/athletes/me`, 'PATCH', {
      nickname: null, position: null, secondaryPosition: null, level: null, preferredSide: null, heightCm: null,
      ...attributes,
    }, person.token);
    assert.equal(saved.userId, person.id);
    for (const [key, value] of Object.entries(attributes)) assert.equal(saved[key], value);
  }
}

async function memberPrivacy(ctx) {
  const { sql, request, api, group, owner, other, publishedGame } = ctx;
  const privatePhone = '+5511987654321';
  await sql(`UPDATE access_users SET phone='${privatePhone}', phone_visibility='NOBODY' WHERE id='${other.id}';`);
  const game = await publishedGame(group, owner.token);
  const attendance = await request(`${api}/api/groups/${group}/games/${game.id}/attendance`, 'PUT', {
    requestId: randomUUID(), intent: 'DECLINE',
  }, other.token);
  assert.equal(attendance.attendance.status, 'DECLINED');
  const charge = await monthly(ctx, other, '2025-01', 98765);
  const stats = await request(`${api}/api/groups/${group}/athletes/${other.id}/stats`, 'GET', undefined, owner.token);
  assert.deepEqual(stats, { games: 1, attendanceRate: 0, absences: 1 });
  return { privatePhone, privateCharge: charge.id };
}

async function monthly(ctx, person, month, amountCents) {
  const { request, api, group, owner } = ctx;
  const response = await request(`${api}/api/groups/${group}/charges/monthly`, 'POST', {
    requestId: randomUUID(), memberIds: [person.id], month, dueDate: `${month}-12`, amountCents,
  }, owner.token);
  assert.equal(response.charges.length, 1);
  const charge = response.charges[0];
  assert.equal(charge.memberId, person.id);
  assert.equal(charge.groupId, group);
  assert.equal(charge.status, 'PENDING');
  return charge;
}

async function monthlyHistory(ctx) {
  const { request, api, sql, group, secondGroup, owner, athlete, other, publishedGame } = ctx;
  const monthlyCharges = [];
  const states = ['PENDING', 'PAID', 'WAIVED', 'CANCELLED'];
  for (let index = 0; index < 12; index += 1) {
    const month = `2025-${String(index + 1).padStart(2, '0')}`;
    let charge = await monthly(ctx, athlete, month, 8000 + index * 100);
    const status = states[index % states.length];
    if (status !== 'PENDING') {
      charge = await request(`${api}/api/groups/${group}/charges/${charge.id}/status`, 'POST', {
        status, note: 'Precondicao do historico E2E', ...(status === 'PAID' ? { paidMethod: 'PIX' } : {}),
      }, owner.token, { 'If-Match': `"${charge.version}"` });
    }
    assert.equal(charge.status, status);
    monthlyCharges.push({
      id: charge.id, month, dueDate: `${month}-12`, amountCents: 8000 + index * 100, status,
    });
  }
  const otherCharge = await monthly(ctx, other, '2025-01', 98765);
  const game = await publishedGame(group, owner.token);
  const gameCharge = randomUUID();
  const groupPix = 'quadra-profile@e2e.saqz.test';
  const secondGroupPix = 'praia-profile@e2e.saqz.test';
  // Historical GAME precondition: the current monthly membership need not match its past billing type.
  await sql(`BEGIN;
    INSERT INTO group_charges (id,group_id,member_user_id,member_display_name,kind,game_id,
      amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at)
    SELECT '${gameCharge}','${group}',id,display_name,'GAME','${game.id}',65432,'2025-01-12',
      'PENDING','${owner.id}','${owner.id}',now(),now() FROM access_users WHERE id='${athlete.id}';
    UPDATE access_groups SET pix_key='${groupPix}',pix_label='Quadra E2E' WHERE id='${group}';
    UPDATE access_groups SET pix_key='${secondGroupPix}',pix_label='Praia E2E' WHERE id='${secondGroup}';
    COMMIT;`);
  const own = await request(`${api}/api/groups/${group}/charges/me`, 'GET', undefined, athlete.token);
  assert.equal(own.charges.length, 13);
  assert.equal(own.charges.filter(charge => charge.kind === 'MONTHLY').length, 12);
  const empty = await request(`${api}/api/groups/${secondGroup}/charges/me`, 'GET', undefined, athlete.token);
  assert.equal(empty.charges.length, 0);
  return { monthlyCharges, otherCharge: otherCharge.id, gameCharge, groupPix, secondGroupPix };
}
