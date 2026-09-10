import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

// Only preconditions are seeded here. Tested preferences, publications, reads and reminders use UI.
export async function seedExtra(ctx) {
  if (ctx.name === 'message-pagination') return seedPagination(ctx);
  if (ctx.name === 'notification-settings') return seedNotifications(ctx);
  if (ctx.name === 'reminders') return seedReminders(ctx);
  return {};
}

async function publish(ctx, channel, body) {
  return ctx.request(`${ctx.api}/api/groups/${ctx.group}/messages?channel=${channel}`, 'POST',
    { requestId: randomUUID(), body }, ctx.owner.token);
}

async function member(ctx, label, role = 'ATHLETE', active = true) {
  const person = await ctx.user(`${ctx.name}-${label}`, ctx.runId);
  await ctx.sql(`INSERT INTO group_memberships
    (group_id,user_id,role,membership_type,active,created_at,updated_at)
    VALUES ('${ctx.group}','${person.id}','${role}','AVULSO',${active},now(),now());`);
  return person;
}

function safe(person) {
  const { token, ...identity } = person;
  return identity;
}

async function seedPagination(ctx) {
  const messages = [];
  for (let index = 1; index <= 53; index++) {
    const message = await publish(ctx, 'CHAT', `Mensagem QA ${String(index).padStart(2, '0')}`);
    messages.unshift({ id: message.id, sequence: message.sequence, body: message.body });
  }
  const page = await ctx.request(`${ctx.api}/api/groups/${ctx.group}/messages?channel=CHAT`, 'GET', undefined, ctx.athlete.token);
  assert.equal(page.items.length, 50);
  assert.equal(page.nextCursor, messages[49].sequence);
  return { messages };
}

async function seedNotifications(ctx) {
  const peer = await member(ctx, 'peer');
  const game = await ctx.publishedGame(ctx.group, ctx.owner.token);
  const gameVenue = 'Arena Notificações QA';
  await ctx.sql(`UPDATE games SET venue_name='${gameVenue}' WHERE id='${game.id}';`);
  const oldChat = await publish(ctx, 'CHAT', 'Conversa anterior às preferências QA');
  const oldNotice = await publish(ctx, 'NOTICE', 'Aviso anterior às preferências QA');
  const reminder = await ctx.request(`${ctx.api}/api/groups/${ctx.group}/games/${game.id}/notify-pending`,
    'POST', { requestId: randomUUID() }, ctx.owner.token);
  assert.equal(reminder.recipientCount, 2);
  const inbox = await ctx.request(`${ctx.api}/api/me/notifications`, 'GET', undefined, ctx.athlete.token);
  assert.equal(inbox.items.length, 3);
  assert.ok(inbox.items.every(item => item.read === false));
  return { peer: safe(peer), game: game.id, gameTitle: game.title, gameVenue,
    oldChat: oldChat.id, oldNotice: oldNotice.id, reminder: reminder.id };
}

async function seedReminders(ctx) {
  const peer = await member(ctx, 'peer');
  const admin = await member(ctx, 'admin', 'ADMIN');
  const declined = await member(ctx, 'declined');
  const inactive = await member(ctx, 'inactive', 'ATHLETE', false);
  const game = await ctx.publishedGame(ctx.group, ctx.owner.token);
  for (const [person, intent, status] of [
    [ctx.athlete, 'CONFIRM', 'CONFIRMED'], [admin, 'CONFIRM', 'CONFIRMED'], [declined, 'DECLINE', 'DECLINED'],
  ]) {
    const response = await ctx.request(`${ctx.api}/api/groups/${ctx.group}/games/${game.id}/attendance`,
      'PUT', { requestId: randomUUID(), intent }, person.token);
    assert.equal(response.attendance.status, status);
  }
  const charges = await ctx.request(`${ctx.api}/api/groups/${ctx.group}/charges`, 'GET', undefined, ctx.owner.token);
  assert.ok(charges.charges.some(charge => charge.gameId === game.id && charge.memberId === admin.id
    && charge.kind === 'GAME' && charge.amountCents === 2000 && charge.status === 'PENDING'));
  const foreignGame = await ctx.publishedGame(ctx.secondGroup, ctx.owner.token);
  const completedGame = randomUUID();
  const expiredGame = randomUUID();
  // Distinct future slots avoid the schedule uniqueness constraint; lifecycle/deadline are preconditions.
  for (const [id, days, status, deadline] of [
    [completedGame, 2, 'COMPLETED', "confirmation_deadline + interval '2 days'"],
    [expiredGame, 3, 'PUBLISHED', "now() - interval '1 hour'"],
  ]) {
    await ctx.sql(`INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,
      duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at)
      SELECT '${id}',group_id,'Jogo inelegível QA',local_date + ${days},local_time,zone_id,
      starts_at + interval '${days} days',duration_minutes,${deadline},venue_name,venue_address,
      capacity,game_fee_cents,'${status}',now(),now() FROM games WHERE id='${game.id}';`);
  }
  return { peer: safe(peer), admin: safe(admin), declined: safe(declined), inactive: safe(inactive),
    game: game.id, gameTitle: game.title, foreignGame: foreignGame.id, completedGame, expiredGame };
}
