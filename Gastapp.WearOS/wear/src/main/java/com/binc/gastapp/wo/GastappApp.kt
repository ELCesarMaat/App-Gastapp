package com.binc.gastapp.wo

import android.app.Application
import com.binc.gastapp.wo.data.ExpenseRepository
import com.binc.gastapp.wo.data.auth.PairingRepository
import com.binc.gastapp.wo.data.auth.TokenStore
import com.binc.gastapp.wo.data.local.AppDatabase
import com.binc.gastapp.wo.data.remote.GastappApi
import com.binc.gastapp.wo.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Contenedor de dependencias hecho a mano. El proyecto es chico y no justifica Hilt.
 */
class GastappApp : Application() {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var api: GastappApi
        private set

    lateinit var repository: ExpenseRepository
        private set

    lateinit var pairingRepository: PairingRepository
        private set

    /** Se vuelve false cuando el refresh falla: el reloj quedo desvinculado. */
    val sessionActive = MutableStateFlow(true)

    override fun onCreate() {
        super.onCreate()

        tokenStore = TokenStore(this)
        api = NetworkModule.create(tokenStore) { sessionActive.value = false }

        val db = AppDatabase.get(this)
        repository = ExpenseRepository(api, db.expenseDao(), db.categoryDao(), db.summaryDao())
        pairingRepository = PairingRepository(api, tokenStore)
    }
}
