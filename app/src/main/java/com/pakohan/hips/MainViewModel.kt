package com.pakohan.hips

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pakohan.hips.pdf.ComposeProblem
import com.pakohan.hips.pdf.ComposeResult
import com.pakohan.hips.pdf.PdfComposer
import com.pakohan.hips.scan.GlyphClassifier
import com.pakohan.hips.scan.PageReader
import com.pakohan.hips.scan.PageReading
import com.pakohan.hips.scan.PdfPages
import com.pakohan.hips.scan.PhotoLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.Executors

sealed interface ScanState {
    data object Idle : ScanState

    data object Working : ScanState

    /** [message] is null when the photo held no message the reader could verify. */
    data class Done(
        val message: String?,
        val lines: Int,
        val symbols: Int,
        val xHeight: Float,
        val packetsDecoded: Int,
        val packetSlots: Int,
        val correctedBits: Int,
        val seconds: Float,
    ) : ScanState

    data class Failed(val reason: String) : ScanState
}

sealed interface WriteState {
    data object Idle : WriteState

    data object Working : WriteState

    data class Done(val pages: Int, val packetsNeeded: Int, val packetSlots: Int) : WriteState

    data class Refused(val problem: ComposeProblem) : WriteState

    data class Failed(val reason: String) : WriteState
}

data class UiState(val scan: ScanState = ScanState.Idle, val write: WriteState = WriteState.Idle)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // The network must be created, run and closed on one thread; the whole scan runs there.
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val scanDispatcher = scanExecutor.asCoroutineDispatcher()
    private var classifier: GlyphClassifier? = null
    private var scanJob: Job? = null

    fun scanPhoto(photo: Uri) = scan { reader -> reader.read(PhotoLoader.load(contentResolver, photo)) }

    /** Reads the pages of a PDF one after the other and stops at the first that holds a message. */
    fun scanPdf(pdf: Uri) =
        scan { reader ->
            PdfPages(contentResolver, pdf).use { pages ->
                var best: PageReading? = null
                for (index in 0 until pages.count) {
                    val reading = reader.readUpright(pages.page(index))
                    if (best == null || reading.reading.packetsDecoded > best.reading.packetsDecoded) best = reading
                    if (reading.reading.message != null) break
                }
                best ?: throw IOException("the PDF has no pages")
            }
        }

    /**
     * Abandons the running scan. The screen is free at once; the scan thread stops at the reader's
     * next checkpoint, which can take a few seconds, and a new scan waits for that.
     */
    fun cancelScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(scan = ScanState.Idle) }
    }

    private fun scan(read: (PageReader) -> PageReading) {
        scanJob?.cancel()
        _uiState.update { it.copy(scan = ScanState.Working) }
        scanJob = viewModelScope.launch(scanDispatcher) {
            val state = try {
                val started = SystemClock.elapsedRealtime()
                val scorer = classifier ?: GlyphClassifier(getApplication()).also { classifier = it }
                val reading = read(PageReader(scorer, checkpoint = { ensureActive() }))
                reading.toState((SystemClock.elapsedRealtime() - started) / 1000f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                ScanState.Failed(e.message.orEmpty())
            } catch (e: RuntimeException) {
                // LiteRT and the image decoder report their failures as unchecked exceptions.
                ScanState.Failed(e.message ?: e.javaClass.simpleName)
            }
            ensureActive() // a cancelled scan must not overwrite what the screen shows now
            _uiState.update { it.copy(scan = state) }
        }
    }

    private val contentResolver get() = getApplication<Application>().contentResolver

    private fun PageReading.toState(seconds: Float) =
        ScanState.Done(
            message = reading.message?.decodeToString(),
            lines = lines,
            symbols = symbols,
            xHeight = xHeight,
            packetsDecoded = reading.packetsDecoded,
            packetSlots = reading.packetSlots,
            correctedBits = reading.correctedBits,
            seconds = seconds,
        )

    /** True when [text] can carry [message]; otherwise the reason becomes the write state. Call before asking for a file. */
    fun canWrite(text: String, message: String): Boolean {
        val problem = PdfComposer(getApplication<Application>().assets).check(text, message.encodeToByteArray())
        _uiState.update { it.copy(write = problem?.let(WriteState::Refused) ?: WriteState.Idle) }
        return problem == null
    }

    /** Placeholder text long enough for [message], or null (with the reason as the write state) when it is too long. */
    fun placeholderText(message: String): String? {
        val bytes = message.encodeToByteArray()
        if (bytes.size > PdfComposer.MAX_MESSAGE_BYTES) {
            _uiState.update { it.copy(write = WriteState.Refused(ComposeProblem.MessageTooLong)) }
            return null
        }
        _uiState.update { it.copy(write = WriteState.Idle) }
        return PdfComposer(getApplication<Application>().assets).placeholderText(bytes)
    }

    fun write(text: String, message: String, target: Uri) {
        _uiState.update { it.copy(write = WriteState.Working) }
        viewModelScope.launch(Dispatchers.IO) {
            val application = getApplication<Application>()
            val state = try {
                val output = application.contentResolver.openOutputStream(target) ?: throw IOException("cannot open $target")
                when (val result = output.use { PdfComposer(application.assets).compose(text, message.encodeToByteArray(), it) }) {
                    is ComposeResult.Written -> WriteState.Done(result.pages, result.packetsNeeded, result.packetSlots)
                    is ComposeResult.Refused -> WriteState.Refused(result.problem)
                }
            } catch (e: IOException) {
                WriteState.Failed(e.message.orEmpty())
            }
            _uiState.update { it.copy(write = state) }
        }
    }

    override fun onCleared() {
        scanExecutor.execute { classifier?.close() }
        scanExecutor.shutdown()
    }
}
