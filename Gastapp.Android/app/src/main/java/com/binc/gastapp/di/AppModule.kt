package com.binc.gastapp.di

import android.content.Context
import com.binc.gastapp.domain.model.MovementTexts
import com.binc.gastapp.ui.format.LocalizedMovementTexts
import com.binc.gastapp.ui.format.ResourceStrings
import com.binc.gastapp.ui.format.Strings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Alcance que vive lo que el proceso: lo que observa Room y DataStore sin pantalla. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** SupervisorJob: si falla un observador, los demas siguen. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Textos traducidos para ViewModels, repositorios y notificaciones. */
    @Provides
    @Singleton
    fun provideStrings(@ApplicationContext context: Context): Strings = ResourceStrings(context)

    /** Titulos de los gastos que crea la app (pagos, ajustes, cobros) en el idioma del telefono. */
    @Provides
    fun provideMovementTexts(texts: LocalizedMovementTexts): MovementTexts = texts
}
