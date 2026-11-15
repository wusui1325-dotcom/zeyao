package com.wusper.zeyao.ai

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Awaits an OkHttp call and cancels the socket when the coroutine is cancelled,
 * so destroying the 驗證中 float does not leave a classify request running.
 */
internal suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isCancelled) {
                    response.close()
                    return
                }
                cont.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled || !cont.isActive) return
                if (call.isCanceled()) {
                    cont.cancel()
                } else {
                    cont.resumeWithException(e)
                }
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
