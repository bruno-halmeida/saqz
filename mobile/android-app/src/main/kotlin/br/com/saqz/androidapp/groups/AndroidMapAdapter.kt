package br.com.saqz.androidapp.groups

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import br.com.saqz.groups.domain.map.GroupMapCallback
import br.com.saqz.groups.domain.map.GroupMapPort

internal class AndroidMapAdapter(private val context: Context) : GroupMapPort {
    override fun open(url: String, done: GroupMapCallback) {
        val opened = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
        done.complete(opened)
    }
}
