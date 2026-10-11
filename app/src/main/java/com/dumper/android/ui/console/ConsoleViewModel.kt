package com.dumper.android.ui.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.dumper.android.R
import com.dumper.android.ui.Event

class ConsoleViewModel : ViewModel() {

    val console = MutableLiveData("")
    val finishCode = MutableLiveData<Event<Int>>()

    /**
     * Backing buffer for the console text.
     *
     * Dumps may run on worker threads (non-root path runs in a coroutine on
     * Dispatchers.IO), where LiveData.setValue() would throw. Appends are
     * synchronized here and dispatched with setValue on the main thread or
     * postValue elsewhere, so no update is ever lost or crashed on.
     */
    private val consoleBuilder = StringBuilder()

    fun copyConsole(context: Context) {
        val text = synchronized(consoleBuilder) { consoleBuilder.toString() }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("PADumper-Log", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.log_copied, Toast.LENGTH_SHORT).show()
    }

    fun append(text: String) {
        val snapshot = synchronized(consoleBuilder) {
            consoleBuilder.append(text)
            consoleBuilder.toString()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            console.value = snapshot
        } else {
            console.postValue(snapshot)
        }
    }

    fun clear() {
        synchronized(consoleBuilder) { consoleBuilder.setLength(0) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            console.value = ""
        } else {
            console.postValue("")
        }
    }

    fun appendLine(text: String) {
        append(text + "\n")
    }

    fun appendLine() {
        append("\n")
    }

    fun appendError(text: String) {
        appendLine("[ERROR] $text")
    }

    fun appendWarning(text: String) {
        appendLine("[WARNING] $text")
    }

    fun appendInfo(text: String) {
        appendLine("[INFO] $text")
    }

    fun appendSuccess(text: String) {
        appendLine("[SUCCESS] $text")
    }

    fun finish(ret: Int) {
        finishCode.postValue(Event(ret))
    }
}