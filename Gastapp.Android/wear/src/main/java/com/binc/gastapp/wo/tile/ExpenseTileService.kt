package com.binc.gastapp.wo.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.binc.gastapp.wo.BuildConfig
import com.binc.gastapp.wo.GastappApp
import com.binc.gastapp.wo.ui.quickadd.QuickAddActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import java.text.NumberFormat
import java.util.Locale

private const val RECURSOS_VERSION = "1"
private const val ID_ABRIR_CAPTURA = "quick_add"
private const val COLOR_PRIMARIO = 0xFF4DD0A7.toInt()
private const val COLOR_TENUE = 0xFFB0BEC5.toInt()

/**
 * Tile con el total del dia y acceso directo a la captura por voz.
 *
 * Lee SIEMPRE de Room, nunca de la red: la API vive en el plan gratuito de Render y
 * un arranque en frio de 50 segundos congelaria el tile. Quien actualiza el cache es
 * el SyncWorker.
 */
class ExpenseTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val app = applicationContext as GastappApp

        val haySesion = app.pairingRepository.hasSession() && app.sessionActive.value
        val resumen = if (haySesion) app.repository.cachedSummary() else null
        val pendientes = if (haySesion) app.repository.pendingCount() else 0

        val cuerpo = if (!haySesion) {
            construirSinSesion()
        } else {
            construirResumen(
                total = resumen?.total ?: 0.0,
                conteo = resumen?.count ?: 0,
                pendientes = pendientes
            )
        }

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RECURSOS_VERSION)
            // Entre 15 y 30 min: el dato no cambia tan seguido como para justificar mas.
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(cuerpo)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = Futures.immediateFuture(
        ResourceBuilders.Resources.Builder()
            .setVersion(RECURSOS_VERSION)
            .build()
    )

    private fun construirSinSesion(): LayoutElement =
        Column.Builder()
            .setModifiers(modificadorClickable())
            .addContent(texto("Gastapp", 14f, COLOR_TENUE))
            .addContent(texto("Toca para vincular", 16f, COLOR_PRIMARIO))
            .build()

    private fun construirResumen(total: Double, conteo: Int, pendientes: Int): LayoutElement {
        val columna = Column.Builder()
            .setModifiers(modificadorClickable())
            .addContent(texto("Hoy", 13f, COLOR_TENUE))
            .addContent(texto(formatearMonto(total), 30f, COLOR_PRIMARIO))
            .addContent(
                texto(
                    if (conteo == 1) "1 gasto" else "$conteo gastos",
                    12f,
                    COLOR_TENUE
                )
            )
            .addContent(texto("+ Nuevo gasto", 15f, COLOR_PRIMARIO))

        if (pendientes > 0) {
            columna.addContent(
                texto(
                    if (pendientes == 1) "1 por sincronizar" else "$pendientes por sincronizar",
                    11f,
                    COLOR_TENUE
                )
            )
        }

        return columna.build()
    }

    private fun texto(valor: String, tamano: Float, color: Int): LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(valor)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(androidx.wear.protolayout.DimensionBuilders.sp(tamano))
                    .setColor(argb(color))
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(dp(2f))
                            .setBottom(dp(2f))
                            .build()
                    )
                    .build()
            )
            .build()

    /** Todo el tile es tocable y abre la captura por voz. */
    private fun modificadorClickable(): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId(ID_ABRIR_CAPTURA)
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                // Del BuildConfig y NO escrito a mano: el applicationId
                                // y el paquete de las clases son distintos (el primero
                                // es com.binc.gastapp, el segundo com.binc.gastapp.wo)
                                // y tenerlo fijo dejo el tile sin hacer nada al pulsarlo
                                // cuando cambio el applicationId.
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(BuildConfig.APPLICATION_ID)
                                    .setClassName(QuickAddActivity::class.java.name)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    private fun formatearMonto(monto: Double): String {
        val formato = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        formato.maximumFractionDigits = if (monto % 1.0 == 0.0) 0 else 2
        return formato.format(monto)
    }
}
