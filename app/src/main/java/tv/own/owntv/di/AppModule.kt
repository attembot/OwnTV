package tv.own.owntv.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import tv.own.owntv.features.customize.CustomizeViewModel
import tv.own.owntv.features.downloads.DownloadsViewModel
import tv.own.owntv.features.epg.EpgViewModel
import tv.own.owntv.features.home.HomeViewModel
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.features.search.SearchViewModel
import tv.own.owntv.features.series.SeriesViewModel
import tv.own.owntv.features.settings.BackupViewModel
import tv.own.owntv.features.settings.HomeSettingsViewModel
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.features.setup.SetupViewModel
import tv.own.owntv.features.shell.ShellViewModel

/**
 * Root Koin module. Each feature will contribute its own bindings as the app grows;
 * for now this wires settings persistence and the shell view model.
 */
val appModule = module {
    single { SettingsRepository(androidContext()) }
    // Merged (v4.0.0 + PR#31 Home/launcher). Koin resolves each get() by type, so only the count must match
    // each ViewModel's merged constructor.
    viewModel { ShellViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SetupViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { LiveViewModel(androidContext(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MovieViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SeriesViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ProfilesViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeSettingsViewModel(get()) }
    viewModel { DownloadsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { EpgViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // settings, sourceDao, categoryDao, customizationStore
    viewModel { CustomizeViewModel(get(), get(), get(), get()) }
    // backupManager
    viewModel { BackupViewModel(get()) }
    // store, epgRepository, sourceRepository, settings, epgDao, channelDao, scheduler
    viewModel { tv.own.owntv.features.settings.EpgSourcesViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
