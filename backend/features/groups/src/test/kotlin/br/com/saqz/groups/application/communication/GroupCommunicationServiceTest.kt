package br.com.saqz.groups.application.communication

import br.com.saqz.groups.domain.GroupRole
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Avisos, chat e lembretes do grupo: quem pode publicar, o que é recusado e o que chega a cada pessoa. */
class GroupCommunicationServiceTest {
    private val group = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val request = UUID.randomUUID()
    private val transaction = CountingTransaction()
    private val groups = FixedRoleGroups(group, GroupRole.OWNER)
    private val repository = MemoryCommunicationRepository()
    private val service = GroupCommunicationService(transaction, groups, repository)

    private val internalChannels = listOf(
        MessageChannel.REMINDER, MessageChannel.GAME_OPEN, MessageChannel.CHARGE, MessageChannel.ATTENDANCE_WINDOW,
        MessageChannel.WAITLIST_DEADLINE, MessageChannel.FIRST_MEMBER, MessageChannel.FIRST_CONFIRMATION,
        MessageChannel.TRIAL_ENDING, MessageChannel.AUTO_CONFIRMATION,
    )

    // --- leitura do chat e dos avisos ---

    @Test fun `quem não é do grupo, ou grupo que não existe, não lê mensagens`() {
        groups.role = null
        assertEquals(failure(CommunicationError.NOT_FOUND), service.messages(actor, group, MessageChannel.CHAT, null))
        groups.role = GroupRole.ATHLETE
        repository.groupExists = false
        assertEquals(failure(CommunicationError.NOT_FOUND), service.messages(actor, group, MessageChannel.CHAT, null))
        assertEquals(failure(CommunicationError.NOT_FOUND), service.messages(actor, UUID.randomUUID(), MessageChannel.CHAT, null))
    }

    @Test fun `só chat e avisos têm histórico, os canais automáticos são recusados`() {
        internalChannels.forEach {
            assertEquals(failure(CommunicationError.INVALID), service.messages(actor, group, it, null), "$it")
        }
        assertTrue(service.messages(actor, group, MessageChannel.CHAT, null) is CommunicationResult.Success)
        assertTrue(service.messages(actor, group, MessageChannel.NOTICE, null) is CommunicationResult.Success)
        assertTrue(service.messages(actor, group, MessageChannel.ENTRY_REQUEST, null) is CommunicationResult.Success)
    }

    @Test fun `o cursor do histórico precisa ser positivo e chega inteiro ao repositório`() {
        assertEquals(failure(CommunicationError.INVALID), service.messages(actor, group, MessageChannel.CHAT, 0))
        assertEquals(failure(CommunicationError.INVALID), service.messages(actor, group, MessageChannel.CHAT, -5))
        service.messages(actor, group, MessageChannel.CHAT, 1)
        assertEquals(1L, repository.lastBefore)
        service.messages(actor, group, MessageChannel.CHAT, null)
        assertNull(repository.lastBefore)
    }

    @Test fun `a página traz 50 mensagens e o cursor aponta a última entregue só quando há mais`() {
        repository.pageOfMessages = (1..51).map { message(group, actor, MessageChannel.CHAT, "m$it", sequence = 100L - it) }
        val page = (service.messages(actor, group, MessageChannel.CHAT, null) as CommunicationResult.Success).value
        assertEquals(50, page.items.size)
        assertEquals("m1", page.items.first().body)
        assertEquals("m50", page.items.last().body)
        assertEquals(50L, page.nextCursor)

        repository.pageOfMessages = repository.pageOfMessages.take(50)
        val last = (service.messages(actor, group, MessageChannel.CHAT, null) as CommunicationResult.Success).value
        assertEquals(50, last.items.size)
        assertNull(last.nextCursor)
    }

    // --- publicar ---

    @Test fun `quem não é do grupo não publica`() {
        groups.role = null
        assertEquals(failure(CommunicationError.NOT_FOUND), service.publish(actor, group, MessageChannel.CHAT, request, "oi"))
        repository.groupExists = false
        groups.role = GroupRole.OWNER
        assertEquals(failure(CommunicationError.NOT_FOUND), service.publish(actor, group, MessageChannel.CHAT, request, "oi"))
        assertTrue(repository.published.isEmpty())
    }

    @Test fun `canal automático nunca é publicado à mão, nem pelo dono`() {
        internalChannels.forEach {
            assertEquals(failure(CommunicationError.CHANNEL_NOT_PUBLISHABLE), service.publish(actor, group, it, request, "oi"), "$it")
        }
        assertTrue(repository.published.isEmpty())
    }

    @Test fun `atleta conversa no chat mas não publica aviso`() {
        groups.role = GroupRole.ATHLETE
        assertEquals(failure(CommunicationError.FORBIDDEN), service.publish(actor, group, MessageChannel.NOTICE, request, "aviso"))
        assertTrue(service.publish(actor, group, MessageChannel.CHAT, request, "oi") is CommunicationResult.Success)
        assertEquals(listOf(MessageChannel.CHAT), repository.published.map { it.channel })
    }

    @Test fun `dono e admin publicam aviso`() {
        listOf(GroupRole.OWNER, GroupRole.ADMIN).forEach { role ->
            groups.role = role
            assertTrue(service.publish(actor, group, MessageChannel.NOTICE, UUID.randomUUID(), "Jogo mudou") is CommunicationResult.Success)
        }
        assertEquals(2, repository.published.count { it.channel == MessageChannel.NOTICE })
    }

    @Test fun `o texto é aparado e grava com o autor, o canal e o pedido certos`() {
        val saved = (service.publish(actor, group, MessageChannel.NOTICE, request, "  Traga bola \n") as CommunicationResult.Success).value
        assertEquals("Traga bola", saved.body)
        assertEquals(listOf(PublishedMessage(group, actor, MessageChannel.NOTICE, request, "Traga bola", null)), repository.published)
    }

    @Test fun `texto vazio ou só espaços é recusado como em branco`() {
        listOf("", "   ", "\n\t ").forEach {
            assertEquals(failure(CommunicationError.BODY_BLANK), service.publish(actor, group, MessageChannel.CHAT, request, it))
        }
    }

    @Test fun `2000 caracteres passam e 2001 não, contados depois de aparar`() {
        assertTrue(service.publish(actor, group, MessageChannel.CHAT, request, "a".repeat(2000)) is CommunicationResult.Success)
        assertTrue(service.publish(actor, group, MessageChannel.CHAT, request, " " + "a".repeat(2000) + " ") is CommunicationResult.Success)
        assertEquals(failure(CommunicationError.BODY_TOO_LONG), service.publish(actor, group, MessageChannel.CHAT, request, "a".repeat(2001)))
    }

    @Test fun `caractere de controle é recusado, mas quebra de linha, tab e retorno de carro passam`() {
        assertEquals(failure(CommunicationError.BODY_CONTROL_CHARACTERS), service.publish(actor, group, MessageChannel.CHAT, request, "oi\u0000"))
        assertEquals(failure(CommunicationError.BODY_CONTROL_CHARACTERS), service.publish(actor, group, MessageChannel.CHAT, request, "a\u0007b"))
        assertTrue(service.publish(actor, group, MessageChannel.CHAT, request, "a\nb\tc\rd") is CommunicationResult.Success)
    }

    @Test fun `texto ofensivo é recusado e nada é gravado`() {
        assertEquals(failure(CommunicationError.OBJECTIONABLE), service.publish(actor, group, MessageChannel.CHAT, request, "que caralho"))
        assertTrue(repository.published.isEmpty())
    }

    @Test fun `repetir o pedido com o mesmo texto devolve a mensagem que já existe sem gravar outra`() {
        val existing = message(group, actor, MessageChannel.CHAT, "oi")
        repository.stored[Triple(actor, MessageChannel.CHAT, request)] = existing
        assertEquals(CommunicationResult.Success(existing), service.publish(actor, group, MessageChannel.CHAT, request, " oi "))
        assertTrue(repository.published.isEmpty())
    }

    @Test fun `repetir o pedido com outro texto é conflito`() {
        repository.stored[Triple(actor, MessageChannel.CHAT, request)] = message(group, actor, MessageChannel.CHAT, "oi")
        assertEquals(failure(CommunicationError.CONFLICT), service.publish(actor, group, MessageChannel.CHAT, request, "tchau"))
        assertTrue(repository.published.isEmpty())
    }

    // --- "Avisar" manual ---

    @Test fun `atleta não dispara o lembrete manual`() {
        groups.role = GroupRole.ATHLETE
        assertEquals(failure(CommunicationError.FORBIDDEN), service.remind(actor, group, game, request))
        groups.role = null
        assertEquals(failure(CommunicationError.NOT_FOUND), service.remind(actor, group, game, request))
    }

    @Test fun `o lembrete manual usa o jogo e a lista do momento e fica ligado ao jogo`() {
        repository.gameForReminder = reminderGame()
        repository.roster = ReminderRoster(listOf("Ana"), listOf("Caio"), listOf("Dora"))
        val sent = (service.remind(actor, group, game, request) as CommunicationResult.Success).value
        val expected = PublishedMessage(
            group, actor, MessageChannel.REMINDER, request,
            reminderBody(reminderGame(), repository.roster), game,
        )
        assertEquals(listOf(expected), repository.published)
        assertEquals(game, sent.gameId)
    }

    @Test fun `o lembrete manual depois do prazo diz Prazo encerrado`() {
        repository.gameForReminder = reminderGame().copy(confirmationDeadline = Instant.now().minusSeconds(3600))
        val sent = (service.remind(actor, group, game, request) as CommunicationResult.Success).value
        assertTrue("\nPrazo encerrado" in sent.body, sent.body)
    }

    @Test fun `jogo que não é desse grupo não gera lembrete`() {
        repository.gameForReminder = null
        assertEquals(failure(CommunicationError.INVALID), service.remind(actor, group, game, request))
        assertTrue(repository.published.isEmpty())
    }

    @Test fun `repetir o lembrete do mesmo jogo devolve o anterior, de outro jogo é conflito`() {
        val existing = message(group, actor, MessageChannel.REMINDER, "lista", gameId = game)
        repository.stored[Triple(actor, MessageChannel.REMINDER, request)] = existing
        repository.gameForReminder = reminderGame()
        assertEquals(CommunicationResult.Success(existing), service.remind(actor, group, game, request))
        assertEquals(failure(CommunicationError.CONFLICT), service.remind(actor, group, UUID.randomUUID(), request))
        assertTrue(repository.published.isEmpty())
    }

    // --- rotinas automáticas ---

    @Test fun `o lembrete automático publica um por grupo, assinado pelo dono, e conta quantos`() {
        val other = candidate(groupId = UUID.randomUUID(), ownerId = UUID.randomUUID())
        val first = candidate(groupId = group, ownerId = owner)
        repository.reminders = listOf(first, other)
        repository.roster = ReminderRoster(listOf("Ana"), emptyList(), emptyList())

        assertEquals(2, service.remindAutomatically())

        assertEquals(listOf(group, other.groupId), repository.published.map { it.groupId })
        assertEquals(listOf(owner, other.ownerId), repository.published.map { it.actor })
        assertEquals(listOf(MessageChannel.REMINDER), repository.published.map { it.channel }.distinct())
        assertEquals(listOf(first.gameId, other.gameId), repository.published.map { it.gameId })
        assertTrue(repository.published.first().body.startsWith("Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central"))
        assertTrue("✅ Confirmados:\nAna" in repository.published.first().body)
        assertEquals(2, repository.published.map { it.requestId }.distinct().size)
        assertEquals(1, transaction.calls)
    }

    @Test fun `sem jogo aberto o lembrete automático não publica nada`() {
        assertEquals(0, service.remindAutomatically())
        assertTrue(repository.published.isEmpty())
    }

    @Test fun `o aviso diário de jogo liberado vai ao canal GAME_OPEN com o texto sem listas`() {
        repository.openGames = listOf(candidate(groupId = group, ownerId = owner))
        assertEquals(1, service.announceOpenGames())
        val sent = repository.published.single()
        assertEquals(PublishedMessage(group, owner, MessageChannel.GAME_OPEN, sent.requestId, openGameBody(reminderGame()), repository.openGames.single().gameId), sent)
        assertEquals(0, GroupCommunicationService(transaction, groups, MemoryCommunicationRepository()).announceOpenGames())
    }

    @Test fun `grupo congelado não recebe lembrete automático, e os outros grupos seguem`() {
        val frozen = candidate(groupId = group, ownerId = owner)
        val paid = candidate(groupId = UUID.randomUUID(), ownerId = UUID.randomUUID())
        repository.reminders = listOf(frozen, paid)

        assertEquals(1, GroupCommunicationService(transaction, groups, repository) { it != group }.remindAutomatically())

        assertEquals(listOf(paid.groupId), repository.published.map { it.groupId })
        assertEquals(listOf(MessageChannel.REMINDER), repository.published.map { it.channel })
    }

    @Test fun `grupo congelado não recebe o jogo liberado das 14h`() {
        val frozen = candidate(groupId = group, ownerId = owner)
        val paid = candidate(groupId = UUID.randomUUID(), ownerId = UUID.randomUUID())
        repository.openGames = listOf(frozen, paid)

        assertEquals(1, GroupCommunicationService(transaction, groups, repository) { it != group }.announceOpenGames())

        assertEquals(listOf(paid.groupId), repository.published.map { it.groupId })
    }

    @Test fun `grupo congelado não recebe o jogo liberado na publicação`() {
        val next = candidate(groupId = group, ownerId = owner)
        repository.single = next

        assertFalse(GroupCommunicationService(transaction, groups, repository) { false }.announceOpenGame(group, next.gameId))
        assertTrue(repository.published.isEmpty())
        assertTrue(GroupCommunicationService(transaction, groups, repository) { true }.announceOpenGame(group, next.gameId))
    }

    @Test fun `publicar o próximo jogo avisa na hora, outro jogo não`() {
        val next = candidate(groupId = group, ownerId = owner)
        repository.single = next
        assertTrue(service.announceOpenGame(group, next.gameId))
        assertEquals(PublishedMessage(group, owner, MessageChannel.GAME_OPEN, repository.published.single().requestId, openGameBody(next.game), next.gameId), repository.published.single())

        repository.published.clear()
        assertFalse(service.announceOpenGame(group, UUID.randomUUID()))
        repository.single = null
        assertFalse(service.announceOpenGame(group, next.gameId))
        assertTrue(repository.published.isEmpty())
    }

    @Test fun `cancelar jogo publicado avisa com a data e a hora do jogo`() {
        val sent = service.announceGameCancelled(group, actor, game, LocalDate.of(2026, 10, 14), LocalTime.of(20, 30))
        assertEquals(listOf<Any>(group, actor, game, "O jogo de quarta 14/10 às 20h30 foi cancelado."), repository.cancelled.single())
        assertEquals(game, sent.gameId)
        assertEquals(1, transaction.calls)
    }

    // --- central de notificações e preferências ---

    @Test fun `a central recusa cursor não positivo e pagina de 50 em 50`() {
        assertEquals(failure(CommunicationError.INVALID), service.inbox(actor, 0))
        assertEquals(failure(CommunicationError.INVALID), service.inbox(actor, -1))
        repository.pageOfInbox = (1..51).map { GroupNotification(60L - it, message(group, owner, MessageChannel.NOTICE, "n$it"), read = false) }
        val page = (service.inbox(actor, 1) as CommunicationResult.Success).value
        assertEquals(1L, repository.lastBefore)
        assertEquals(50, page.items.size)
        assertEquals(10L, page.nextCursor)
        repository.pageOfInbox = repository.pageOfInbox.take(50)
        assertNull((service.inbox(actor, null) as CommunicationResult.Success).value.nextCursor)
    }

    @Test fun `marcar como lida e salvar preferências vão ao repositório da pessoa certa`() {
        service.markRead(actor, 7)
        assertEquals(listOf(actor to 7L), repository.read)
        val prefs = NotificationPreferences(notices = false)
        assertEquals(prefs, service.savePreferences(actor, prefs))
        assertEquals(listOf(actor to prefs), repository.savedPreferences)
        assertEquals(repository.preferences, service.preferences(actor))
    }

    @Test fun `preferências nascem com push ligado, WhatsApp só para cobrança e a cobrança segue o lembrete`() {
        val defaults = NotificationPreferences()
        assertEquals(PushPreferences(true, true, true, true), defaults.push)
        assertEquals(WhatsAppPreferences(notices = false, reminders = false, charges = true), defaults.whatsapp)
        val off = NotificationPreferences(notices = false, messages = false, reminders = false)
        assertEquals(PushPreferences(notices = false, messages = false, reminders = false, charges = false), off.push)
    }

    private fun failure(error: CommunicationError) = CommunicationResult.Failure(error)
    private fun reminderGame() = ReminderGame(LocalDate.of(2026, 9, 20), LocalTime.of(19, 30), "Arena Central")
    private fun candidate(groupId: UUID, ownerId: UUID) = ReminderCandidate(UUID.randomUUID(), groupId, ownerId, reminderGame())
}
