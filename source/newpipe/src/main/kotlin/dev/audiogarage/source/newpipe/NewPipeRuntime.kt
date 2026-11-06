package dev.audiogarage.source.newpipe

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe

/** NewPipe keeps its HTTP bridge in a static field: initialise it once per process. */
object NewPipeRuntime {
    @Volatile
    private var initialized = false

    fun ensureInitialized(client: OkHttpClient) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(OkHttpNewPipeDownloader(client))
                initialized = true
            }
        }
    }
}
