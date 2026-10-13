package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.TrialPromptStore
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class JdbcTrialPromptStore(dataSource: DataSource) : TrialPromptStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lastPromptDate(ownerUserId: UUID): LocalDate? =
        jdbc.sql("SELECT last_prompt_date FROM organizer_trial_prompts WHERE owner_user_id = :owner")
            .param("owner", ownerUserId).query(LocalDate::class.java).optional().orElse(null)

    override fun markPrompted(ownerUserId: UUID, date: LocalDate): Boolean = jdbc.sql(
        """
        INSERT INTO organizer_trial_prompts (owner_user_id, last_prompt_date)
        SELECT owner_user_id, :date FROM organizer_trials WHERE owner_user_id = :owner
        ON CONFLICT (owner_user_id) DO UPDATE
            SET last_prompt_date = GREATEST(organizer_trial_prompts.last_prompt_date, EXCLUDED.last_prompt_date)
        """.trimIndent(),
    ).param("owner", ownerUserId).param("date", date).update() > 0
}
