package com.binc.gastapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Punto de entrada de Hilt. El arranque (refrescar el token, empujar lo pendiente y
 * programar los trabajos de WorkManager) se agrega aqui en las Fases 3 y 5.
 */
@HiltAndroidApp
class GastappApplication : Application()
