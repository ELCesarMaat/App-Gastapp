package com.binc.gastapp.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.prefs.RegisterDraftStore
import com.binc.gastapp.data.prefs.SessionStore
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.notifications.AndroidAppNotifier
import com.binc.gastapp.notifications.AppNotifier
import com.binc.gastapp.notifications.CardReminderScheduler
import com.binc.gastapp.notifications.ReminderScheduler
import com.binc.gastapp.notifications.WorkManagerCardReminderScheduler
import com.binc.gastapp.notifications.WorkManagerReminderScheduler
import com.binc.gastapp.sync.SyncScheduler
import com.binc.gastapp.sync.WorkManagerSyncScheduler
import com.binc.gastapp.wear.PlayServicesWearChannel
import com.binc.gastapp.wear.WearChannel
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GastappDatabase =
        Room.databaseBuilder(context, GastappDatabase::class.java, GastappDatabase.NAME).build()

    /** Hora de la app. Las pruebas pasan un Clock fijo. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    // Tres archivos de DataStore separados: al cerrar sesion se borran la sesion y el
    // borrador, pero los ajustes del telefono se quedan.

    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        SessionStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") })

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") })

    @Provides
    @Singleton
    fun provideRegisterDraftStore(@ApplicationContext context: Context, clock: Clock): RegisterDraftStore =
        RegisterDraftStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("register_draft") }, clock)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    /** Cada escritura local encola el trabajo unico "sync" de WorkManager. */
    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun bindAppNotifier(impl: AndroidAppNotifier): AppNotifier

    @Binds
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindCardReminderScheduler(impl: WorkManagerCardReminderScheduler): CardReminderScheduler
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {
    /** Data Layer de Play Services; las pruebas usan un canal falso. */
    @Binds
    abstract fun bindWearChannel(impl: PlayServicesWearChannel): WearChannel
}
