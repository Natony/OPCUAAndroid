package com.example.s7opcuaapp.di

import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.buffer.PlcDataBuffer
import com.example.s7opcuaapp.data.local.AppDatabase
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.data.repository.*
import com.example.s7opcuaapp.util.ButtonLockConfig
import com.example.s7opcuaapp.util.ButtonLockRules
import com.example.s7opcuaapp.util.PerformanceMonitor
import com.example.s7opcuaapp.util.StatusLockConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun providePerformanceMonitor(): PerformanceMonitor {
        return PerformanceMonitor()
    }

    @Provides
    @Singleton
    fun providePlcDataBuffer(
        performanceMonitor: PerformanceMonitor
    ): PlcDataBuffer {
        return PlcDataBuffer(performanceMonitor)
    }


    @Provides
    @Singleton
    fun provideStatusLockConfig(prefsManager: PrefsManager): StatusLockConfig {
        return StatusLockConfig(prefsManager)
    }

    @Provides
    @Singleton
    fun provideButtonLockConfig(statusLockConfig: StatusLockConfig): ButtonLockConfig {
        return ButtonLockConfig(statusLockConfig)
    }

    @Provides
    @Singleton
    fun provideButtonLockRules(prefsManager: PrefsManager): ButtonLockRules {
        return ButtonLockRules(prefsManager)
    }

    @Provides
    @Singleton
    fun providePlcApiClient(prefsManager: PrefsManager): PlcApiClient {
        // Use saved API server config
        val serverUrl = prefsManager.getApiServerUrl()
        return PlcApiClient(serverUrl)
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        database: AppDatabase,
        prefsManager: PrefsManager
    ): UserRepository {
        return UserRepositoryImpl(database, prefsManager)
    }

    @Provides
    @Singleton
    fun provideLogRepository(
        database: AppDatabase
    ): LogRepository {
        return LogRepositoryImpl(database)
    }

    /**
     * Resolve the active S7Repository through the provider on each injection so the
     * ViewModel always sees whichever implementation is currently active.
     * Intentionally NOT @Singleton — we want fresh resolution after a mode switch.
     * RepositoryProvider itself is constructor-injected as a @Singleton.
     */
    @Provides
    fun provideS7Repository(provider: RepositoryProvider): S7Repository = provider.current()
}