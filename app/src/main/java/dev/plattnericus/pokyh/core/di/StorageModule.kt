package dev.plattnericus.pokyh.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Storage-Layer ist vollständig konstruktor-injizierbar (`@ApplicationContext Context` reicht
 * für [dev.plattnericus.pokyh.data.storage.SecureCredentialStore],
 * [dev.plattnericus.pokyh.data.storage.PreferencesStore],
 * [dev.plattnericus.pokyh.data.storage.DiskCache] und
 * [dev.plattnericus.pokyh.data.storage.ImageDiskCache]) — kein `@Provides` nötig. Dieses Modul
 * existiert als fester Anker für zukünftige Storage-Bindings, die keinen eigenen Konstruktor
 * haben (z. B. Interface-Bindings).
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule
