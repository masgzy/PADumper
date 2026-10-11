package com.dumper.android.messager

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.dumper.android.IDumperInterface
import com.dumper.android.core.MainActivity
import com.dumper.android.dumper.DumperConfig
import com.dumper.android.dumper.process.ProcessData

class AIDLServiceConnection(private val activity: MainActivity) : ServiceConnection {

    private var iDumperInterface: IDumperInterface? = null

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        activity.console.appendInfo("RootService: Service connected")
        iDumperInterface = IDumperInterface.Stub.asInterface(service)
        activity.dumperConnection = this
    }

    fun getListProcess(): List<ProcessData>? = iDumperInterface?.getListProcess()

    fun dump(config: DumperConfig) {
        Thread {
            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            // The binder call blocks until the root-side dump completes. Run
            // it on its own thread so the reader below can drain the pipe
            // concurrently - otherwise a full 64 KB pipe buffer would block
            // the root process forever (classic deadlock).
            Thread {
                runCatching {
                    iDumperInterface?.dump(config, writeSide)
                }.onFailure { e ->
                    activity.runOnUiThread {
                        activity.console.appendError(e.message ?: "Dump binder call failed")
                    }
                }
                // The remote holds its own dup of this fd (transferred during
                // the binder transaction), so closing our copy here is safe
                // and lets the reader below reach EOF.
                runCatching { writeSide.close() }
            }.start()

            // Read with a Reader: it decodes UTF-8 incrementally, so
            // multi-byte characters split across chunk boundaries no longer
            // turn into garbage (the old code decoded every 1 KB chunk
            // independently).
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(readSide).use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                        activity.runOnUiThread {
                            activity.console.appendLine(line)
                        }
                    }
                }
            }.onFailure { e ->
                activity.runOnUiThread {
                    activity.console.appendError(e.message ?: "Failed to read dump logs")
                }
            }
        }.start()
    }

    override fun onServiceDisconnected(name: ComponentName) {
        activity.console.appendInfo("RootService: Service disconnected")
        activity.dumperConnection = null
    }
}