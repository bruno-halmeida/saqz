package br.com.saqz.groups.presentation

import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.groups.port.NativeInviteSharePort

class FakeInviteSharePort : NativeInviteSharePort {
    var sharedText: String? = null
    var result: InviteNativeOperationResult = InviteNativeOperationResult.Success
    override fun shareText(text: String, done: (InviteNativeOperationResult) -> Unit) {
        sharedText = text
        done(result)
    }
    override fun shareImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) = done(result)
    override fun saveImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) = done(result)
}
