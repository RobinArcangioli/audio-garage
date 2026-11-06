package dev.audiogarage

import android.app.Application
import dev.audiogarage.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AudioGarageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AudioGarageApp)
            modules(appModules)
        }
    }
}
