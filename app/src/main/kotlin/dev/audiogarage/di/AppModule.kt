package dev.audiogarage.di

import dev.audiogarage.BuildConfig
import dev.audiogarage.core.data.dataModule
import dev.audiogarage.core.model.BuildInfo
import dev.audiogarage.feature.downloader.downloaderModule
import dev.audiogarage.feature.player.playerModule
import dev.audiogarage.ui.MainViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single {
        BuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            debug = BuildConfig.DEBUG,
        )
    }
    viewModelOf(::MainViewModel)
}

/** Every Koin module of the app, in one place so the configuration test covers them all. */
val appModules = listOf(appModule, dataModule, downloaderModule, playerModule)
