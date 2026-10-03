package com.binc.gastapp.wo.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.binc.gastapp.wo.GastappApp
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GastappApp
        val repository = app.repository

        return try {
            // Subir los gastos es lo unico que no se puede perder. Va primero y solo
            // esto decide si el trabajo se reintenta.
            repository.pushPending()

            // Refrescar categorias, resumen y lista alimenta el tile y la pantalla
            // principal. Si falla, no vale la pena reintentar todo el trabajo: los
            // gastos ya se subieron y ambos pueden seguir mostrando el dato anterior
            // hasta la siguiente pasada.
            refrescarCache(repository)

            // Los datos ya estan en la base; el tile no se entera hasta que se le pide.
            app.refrescarTile()

            Result.success()
        } catch (e: IOException) {
            // Sin red, timeout, o la API despertando del arranque en frio de Render.
            // Siempre transitorio: reintentar.
            Log.i(TAG, "Sincronizacion pospuesta: ${e.message}")
            Result.retry()
        } catch (e: HttpException) {
            when (e.code()) {
                // El TokenAuthenticator ya intento refrescar. Si sigue en 401, la sesion
                // se perdio; reintentar mas tarde por si el usuario vuelve a vincular.
                401, 403 -> Result.retry()
                in 500..599 -> Result.retry()
                else -> {
                    // 4xx de validacion: reintentar no lo arregla y drena la bateria.
                    Log.w(TAG, "Error permanente al sincronizar: ${e.code()}")
                    marcarFallidos()
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo inesperado al sincronizar", e)
            Result.retry()
        }
    }

    private suspend fun refrescarCache(repository: com.binc.gastapp.wo.data.ExpenseRepository) {
        runCatching { repository.refreshCategories() }
            .onFailure { Log.i(TAG, "No se pudieron refrescar las categorias: ${it.message}") }

        runCatching { repository.refreshSummary() }
            .onFailure { Log.i(TAG, "No se pudo refrescar el resumen: ${it.message}") }

        runCatching { repository.refreshDayExpenses() }
            .onFailure { Log.i(TAG, "No se pudieron refrescar los gastos del dia: ${it.message}") }
    }

    private suspend fun marcarFallidos() {
        val app = applicationContext as GastappApp
        val dao = com.binc.gastapp.wo.data.local.AppDatabase.get(app).expenseDao()
        dao.getUnsynced().forEach { gasto ->
            dao.incrementAttempts(gasto.id)
            if (gasto.syncAttempts + 1 >= MAX_INTENTOS) {
                dao.markFailed(gasto.id)
            }
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_INTENTOS = 5
        private const val TRABAJO_UNICO = "gastapp-sync"
        private const val TRABAJO_PERIODICO = "gastapp-sync-periodico"

        private val restricciones = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Se llama justo despues de guardar un gasto. */
        fun enqueue(context: Context) {
            val trabajo = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(restricciones)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                TRABAJO_UNICO,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                trabajo
            )
        }

        /** Red de seguridad: refresca resumen y categorias aunque no haya pendientes. */
        fun schedulePeriodic(context: Context) {
            val trabajo = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(restricciones)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TRABAJO_PERIODICO,
                ExistingPeriodicWorkPolicy.KEEP,
                trabajo
            )
        }
    }
}
