package com.anish1050.ledger.data.export

import android.content.Context
import android.net.Uri
import com.anish1050.ledger.container
import com.anish1050.ledger.data.db.LedgerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

// Plain SQLite file export/import. Used to move between the play and foss
// builds, and as an offline backup the user owns.
object DbExport {
    suspend fun exportTo(context: Context, target: Uri) = withContext(Dispatchers.IO) {
        val db = context.container.db
        // Fold the WAL into the main file so the copy is complete.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val source = context.getDatabasePath(LedgerDatabase.FILE_NAME)
        context.contentResolver.openOutputStream(target)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
    }

    suspend fun importFrom(context: Context, source: Uri) {
        withContext(Dispatchers.IO) {
            context.container.db.close()
            val target = context.getDatabasePath(LedgerDatabase.FILE_NAME)
            listOf("", "-wal", "-shm").forEach { suffix -> java.io.File(target.path + suffix).delete() }
            context.contentResolver.openInputStream(source)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        // Simplest way to drop every cached DAO and Flow: restart the process.
        exitProcess(0)
    }
}
