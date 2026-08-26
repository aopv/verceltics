package com.apoorvdarshan.verceltics.data.network

/** A single-use blocking call that can be cancelled from another thread. */
interface CancelableCall<T> {
    /** Execute on a worker thread, never the Android main thread. */
    fun execute(): T

    fun cancel()
}

internal class MappingCall<I, O>(
    private val upstream: CancelableCall<I>,
    private val transform: (I) -> O,
) : CancelableCall<O> {
    override fun execute(): O = transform(upstream.execute())

    override fun cancel() = upstream.cancel()
}

internal fun <I, O> CancelableCall<I>.map(transform: (I) -> O): CancelableCall<O> =
    MappingCall(this, transform)
