package com.tony.explorador

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class ExploradorApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // si la app truena, guarda el error para mostrarlo la próxima vez
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                java.io.File(filesDir, "ultimo_error.txt").writeText(
                    "Explorador Tony v" + currentVersionName(this) + " · Android " + android.os.Build.VERSION.RELEASE +
                        " · hilo " + t.name + "\n" + android.util.Log.getStackTraceString(e).take(6000))
            }
            previous?.uncaughtException(t, e)
        }
        runCatching { ExitReport.check(this) }
        TagOverrides.init(this)
        LibraryStore.init(this)
        Trash.init(this)
        NasSync.init(this)
        LyricsLocal.init(this)
        PhotoText.init(this)
        AnimatedCovers.init(this)
        CoverScanner.init(this)
        AnimatedScanner.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
