package com.apoorvdarshan.verceltics.data.account

interface AtomicBytesStore {
    fun read(): ByteArray?

    fun write(bytes: ByteArray)

    fun delete()
}
