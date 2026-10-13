package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.ProfilePhotoCallback
import br.com.saqz.access.domain.port.ProfilePhotoResult
import br.com.saqz.profile.domain.ProfilePhotoSelectionCallback
import br.com.saqz.profile.domain.ProfilePhotoSelectionCancelable
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.domain.ProfilePhotoSelectionResult
import br.com.saqz.webapp.groups.WebGroupPhotoAdapters
import kotlinx.coroutines.CoroutineScope

// STUB: substituir pela escolha de foto via <input type="file"> + canvas, reaproveitando WebGroupPhotoAdapters.
internal class WebProfilePhotoAdapter(
    private val photos: WebGroupPhotoAdapters,
    private val scope: CoroutineScope,
) : NativeProfilePhotoPort, ProfilePhotoSelectionPort {
    override fun chooseCamera(done: ProfilePhotoCallback): Cancelable = failed(done)
    override fun chooseLibrary(done: ProfilePhotoCallback): Cancelable = failed(done)

    override fun chooseCamera(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable = failed(done)
    override fun chooseLibrary(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable = failed(done)

    private fun failed(done: ProfilePhotoCallback): Cancelable {
        done.complete(ProfilePhotoResult.Failed)
        return object : Cancelable { override fun cancel() = Unit }
    }

    private fun failed(done: ProfilePhotoSelectionCallback): ProfilePhotoSelectionCancelable {
        done.complete(ProfilePhotoSelectionResult.Failed)
        return object : ProfilePhotoSelectionCancelable { override fun cancel() = Unit }
    }
}
