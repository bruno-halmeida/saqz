package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.scheduling.GameFeeUndefinedNoticeJob
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGameFeeUndefinedNotices
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GameFeeUndefinedNotices
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Aviso único a dono e admins dos grupos sem valor por jogo (DEB-146, JOGO-008). Desligado de fábrica
 * (`SAQZ_GROUPS_GAME_FEE_NOTICE_ENABLED`): o toque leva ao "Valor por jogo", que só o app novo tem.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.groups.game-fee-notice", name = ["enabled"], havingValue = "true")
class GameFeeUndefinedNoticeConfiguration {
    @Bean fun gameFeeUndefinedNotices(
        transaction: JdbcTransactionRunner,
        dataSource: DataSource,
        communication: JdbcGroupCommunicationRepository,
    ) = GameFeeUndefinedNotices(transaction, JdbcGameFeeUndefinedNotices(dataSource, communication))

    @Bean fun gameFeeUndefinedNoticeJob(notices: GameFeeUndefinedNotices) = GameFeeUndefinedNoticeJob(notices)
}
