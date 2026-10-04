package com.binc.gastapp.di

import com.binc.gastapp.BuildConfig
import com.binc.gastapp.core.remote.ApiClient
import com.binc.gastapp.core.remote.GastappApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Cliente del API (en :core). Sin interceptor de logs a proposito: el login lleva la
     * contrasena en el cuerpo y PasswordReset/confirm la lleva en la URL.
     */
    @Provides
    @Singleton
    fun provideGastappApi(): GastappApi = ApiClient.create(BuildConfig.API_BASE_URL)
}
