import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

/** Preconditions only: the installed app performs every successful financial mutation under test. */
export async function seedExtra(ctx) {
  if (!['payments', 'charge-lifecycle', 'settlement'].includes(ctx.name)) return {};
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
  if (ctx.name === 'settlement') return seedSettlement(ctx);
  const months = ctx.name === 'payments' ? [today.slice(0, 7)] : ['2026-06', '2026-07'];
  const charges = [];
  for (const [index, month] of months.entries()) {
    const generated = await ctx.request(`${ctx.api}/api/groups/${ctx.group}/charges/monthly`, 'POST', {
      requestId: randomUUID(), month, amountCents: 12345 + index * 1000,
      dueDate: `${month}-12`, memberIds: [ctx.athlete.id],
    }, ctx.owner.token);
    assert.equal(generated.charges.length, 1);
    charges.push(generated.charges[0]);
  }
  const persisted = await ctx.request(`${ctx.api}/api/groups/${ctx.group}/charges`, 'GET', undefined, ctx.owner.token);
  assert.equal(persisted.charges.length, months.length);
  assert.ok(persisted.charges.every(charge => charge.status === 'PENDING' && charge.memberId === ctx.athlete.id));
  return { financialCharges: charges.map(charge => charge.id), financialToday: today };
}

async function seedSettlement(ctx) {
  const { api, group, owner, athlete, other, sql, request, publishedGame } = ctx;
  // The additional administrator sends a real navigation reminder to the owner before completion.
  // This leaves a normal UI route to the completed game, including after its pending card disappears.
  await sql(`BEGIN;
    UPDATE access_groups SET default_game_fee_cents=7000 WHERE id='${group}';
    INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at)
      VALUES ('${group}','${other.id}','ADMIN','AVULSO',now(),now());
    COMMIT;`);
  const game = await publishedGame(group, owner.token);
  assert.equal(game.gameFeeCents, 7000);
  const attendance = await request(`${api}/api/groups/${group}/games/${game.id}/attendance`, 'PUT',
    { requestId: randomUUID(), intent: 'CONFIRM' }, athlete.token);
  assert.equal(attendance.attendance.status, 'CONFIRMED');
  const generated = await request(`${api}/api/groups/${group}/charges`, 'GET', undefined, owner.token);
  assert.equal(generated.charges.length, 1, 'Attendance must create exactly one real GAME charge');
  const charge = generated.charges[0];
  assert.equal(charge.kind, 'GAME');
  assert.equal(charge.memberId, athlete.id);
  assert.equal(charge.gameId, game.id);
  assert.equal(charge.amountCents, 7000);
  assert.equal(charge.status, 'PENDING');
  const reminder = await request(`${api}/api/groups/${group}/games/${game.id}/notify-pending`, 'POST',
    { requestId: randomUUID() }, other.token);
  const inbox = await request(`${api}/api/me/notifications`, 'GET', undefined, owner.token);
  const notification = inbox.items.find(item => item.message.id === reminder.id);
  assert.ok(notification, 'Owner needs a real notification route to the completed game');
  const current = await request(`${api}/api/groups/${group}/games/${game.id}`, 'GET', undefined, owner.token);
  const completed = await request(`${api}/api/groups/${group}/games/${game.id}/complete`, 'POST',
    undefined, owner.token, { 'If-Match': `"${current.version}"` });
  assert.equal(completed.status, 'COMPLETED');
  const after = await request(`${api}/api/groups/${group}/charges`, 'GET', undefined, owner.token);
  assert.deepEqual(after, generated, 'Game completion must preserve the existing financial precondition');
  return { game: game.id, financialCharges: [charge.id], settlementNotification: notification.sequence };
}
