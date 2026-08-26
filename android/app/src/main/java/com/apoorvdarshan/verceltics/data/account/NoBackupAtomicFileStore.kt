package com.apoorvdarshan.verceltics.data.account

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/** Atomic storage rooted in [Context.getNoBackupFilesDir], so credentials never enter backups. */
class NoBackupAtomicFileStore(
    context: Context,
    relativePath: String,
) : AtomicBytesStore {
    private val atomicFile: AtomicFile

    init {
        require(relativePath.isNotBlank()) { "A storage path is required." }
        require(!relativePath.startsWith('/') && !relativePath.contains("..")) {
            "The storage path must remain inside noBackupFilesDir."
        }
        val noBackupRoot = context.applicationContext.noBackupFilesDir.canonicalFile
        val target = File(noBackupRoot, relativePath).canonicalFile
        require(target.path.startsWith(noBackupRoot.path + File.separator)) {
            "The storage path escaped noBackupFilesDir."
        }
        val parent = checkNotNull(target.parentFile) { "The secure storage path has no parent." }
        check(parent.isDirectory || parent.mkdirs() || parent.isDirectory) {
            "Unable to create secure storage directory."
        }
        atomicFile = AtomicFile(target)
    }

    @Synchronized
    override fun read(): ByteArray? = try {
        atomicFile.readFully()
    } catch (_: FileNotFoundException) {
        null
    }

    @Synchronized
    override fun write(bytes: ByteArray) {
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            atomicFile.finishWrite(stream)
            stream = null
        } finally {
            stream?.let(atomicFile::failWrite)
        }
    }

    @Synchronized
    override fun delete() = atomicFile.delete()
}
