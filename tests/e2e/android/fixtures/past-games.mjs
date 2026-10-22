import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

/**
 * JOGO-011 + PRES-016: um jogo encerrado de anteontem com um avulso confirmado e a diária pendente.
 * Só precondição: marcar a falta, dispensar e desfazer são ações de UI.
 */
export async function seedExtra(ctx) {
  if (ctx.name !== 'past-games-no-show') return {};
  const { api, group, owner, athlete, sql, request, publishedGame } = ctx;
  await sql(`BEGIN;
    UPDATE access_groups SET default_game_fee_cents=7000 WHERE id='${group}';
    -- DEB-021: só avulso paga diária.
    UPDATE group_memberships SET membership_type='AVULSO' WHERE group_id='${group}' AND user_id='${athlete.id}';
    COMMIT;`);
  const game = await publishedGame(group, owner.token);
  // Sem resposta ainda, o atleta recebe o lembrete: é a rota de UI dele até o jogo depois de encerrado.
  const reminder = await request(`${api}/api/groups/${group}/games/${game.id}/notify-pending`, 'POST',
    { requestId: randomUUID() }, owner.token);
  const inbox = await request(`${api}/api/me/notifications`, 'GET', undefined, athlete.token);
  const notification = inbox.items.find(item => item.message.id === reminder.id);
  assert.ok(notification, 'Athlete needs a real notification route to the completed game');
  const attendance = await request(`${api}/api/groups/${group}/games/${game.id}/attendance`, 'PUT',
    { requestId: randomUUID(), intent: 'CONFIRM' }, athlete.token);
  assert.equal(attendance.attendance.status, 'CONFIRMED');
  const current = await request(`${api}/api/groups/${group}/games/${game.id}`, 'GET', undefined, owner.token);
  const completed = await request(`${api}/api/groups/${group}/games/${game.id}/complete`, 'POST',
    undefined, owner.token, { 'If-Match': `"${current.version}"` });
  assert.equal(completed.status, 'COMPLETED');
  // O jogo nasce amanhã (a API não cria no passado); leva-o para anteontem como se tivesse acontecido.
  // A janela da falta (início + duração + 7 dias) continua aberta.
  await sql(`UPDATE games SET starts_at=starts_at - interval '3 days', local_date=local_date - 3,
    confirmation_deadline=confirmation_deadline - interval '3 days' WHERE id='${game.id}';`);
  const charges = await request(`${api}/api/groups/${group}/charges`, 'GET', undefined, owner.token);
  assert.equal(charges.charges.length, 1);
  const charge = charges.charges[0];
  assert.equal(charge.kind, 'GAME');
  assert.equal(charge.memberId, athlete.id);
  assert.equal(charge.amountCents, 7000);
  assert.equal(charge.status, 'PENDING');
  const past = await request(`${api}/api/groups/${group}/games/past`, 'GET', undefined, owner.token);
  assert.deepEqual(past.games.map(item => item.id), [game.id]);
  assert.equal(past.games[0].pendingChargeCount, 1);
  return { game: game.id, noShowCharge: charge.id, athleteNotification: notification.sequence };
}
