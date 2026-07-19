package br.com.saqz.groups.adapter.output.jdbc.group.create

import br.com.saqz.groups.application.create.CreateGroupCommand
import br.com.saqz.groups.application.create.GroupCreationRepository
import br.com.saqz.groups.application.create.StoredGroup
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.IanaTimeZone
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupCreationRepository(
    dataSource: DataSource,
) : GroupCreationRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun create(command: CreateGroupCommand): StoredGroup = jdbc.sql(
        """
        INSERT INTO access_groups (
            id, owner_user_id, creation_key, name, time_zone, version, created_at, updated_at
        ) VALUES (
            :id, :ownerUserId, :creationKey, :name, :timeZone, 1, now(), now()
        )
        ON CONFLICT (owner_user_id, creation_key) DO UPDATE SET
            creation_key = access_groups.creation_key
        RETURNING id, owner_user_id, creation_key, name, time_zone, version
        """.trimIndent(),
    )
        .param("id", UUID.randomUUID())
        .param("ownerUserId", command.ownerUserId)
        .param("creationKey", command.creationKey)
        .param("name", command.name.value)
        .param("timeZone", command.timeZone.value)
        .query { result, _ ->
            StoredGroup(
                id = result.getObject("id", UUID::class.java),
                ownerUserId = result.getObject("owner_user_id", UUID::class.java),
                creationKey = result.getObject("creation_key", UUID::class.java),
                name = AccessName.from(result.getString("name")),
                timeZone = IanaTimeZone.from(result.getString("time_zone")),
                version = result.getLong("version"),
            )
        }
        .single()
}
