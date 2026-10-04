package com.binc.gastapp.update

import androidx.test.core.app.ApplicationProvider
import com.binc.gastapp.core.remote.ApiClient
import com.binc.gastapp.core.remote.AppLatestVersionDto
import com.binc.gastapp.ui.MainDispatcherRule
import com.binc.gastapp.ui.format.ResourceStrings
import com.binc.gastapp.ui.update.UpdateUiState
import com.binc.gastapp.ui.update.UpdateViewModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdaterTest {

    @get:Rule
    val main = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val strings by lazy { ResourceStrings(context) }

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun updater(manifest: Boolean = true) = AppUpdater(
        context = context,
        api = ApiClient.create(server.url("/api/").toString()),
        client = OkHttpClient(),
        installed = InstalledVersion(code = 200, name = "2.0.0"),
        manifestUrl = if (manifest) server.url("/version.json").toString() else "",
    )

    private fun versionJson(code: Int, apk: String = server.url("/gastapp.apk").toString()) =
        """{"versionCode":$code,"versionName":"2.0.$code","apkUrl":"$apk","releaseNotes":"Notas"}"""

    @Test
    fun `ofrece la version solo si su versionCode es mayor`() = runTest {
        server.enqueue(MockResponse().setBody(versionJson(201)))
        val available = updater().check() as UpdateCheck.Available
        assertEquals(201, available.latest.versionCode)
        assertEquals("/version.json", server.takeRequest().path)

        server.enqueue(MockResponse().setBody(versionJson(200)))
        assertEquals(UpdateCheck.UpToDate, updater().check())

        // MAUI publicada va en la 131: la app nativa no debe ofrecerla.
        server.enqueue(MockResponse().setBody(versionJson(131)))
        assertEquals(UpdateCheck.UpToDate, updater().check())

        server.enqueue(MockResponse().setBody(versionJson(205, apk = "")))
        assertEquals("Sin APK no hay que ofrecer", UpdateCheck.UpToDate, updater().check())

        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(UpdateCheck.Failed, updater().check())
    }

    @Test
    fun `sin version json de prueba pregunta al API`() = runTest {
        server.enqueue(MockResponse().setBody(versionJson(250)))
        assertTrue(updater(manifest = false).check() is UpdateCheck.Available)
        assertEquals("/api/App/LatestVersion", server.takeRequest().path)
    }

    @Test
    fun `descarga con progreso y rechaza lo que no es una actualizacion de Gastapp`() = runTest {
        val bytes = ByteArray(300_000) { 1 }
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val progress = mutableListOf<Float?>()
        try {
            updater().download(AppLatestVersionDto(201, "2.0.1", server.url("/gastapp.apk").toString())) { progress += it }
            fail("Un archivo que no es APK no se debe instalar")
        } catch (e: IOException) {
            assertEquals("El archivo descargado no es una actualización válida de Gastapp.", e.message)
        }
        assertTrue(progress.isNotEmpty())
        assertEquals(1f, progress.last()!!, 0.0001f)
        assertFalse("No queda el archivo a medias", File(context.cacheDir, "gastapp-update.apk.part").exists())

        server.enqueue(MockResponse().setResponseCode(404))
        try {
            updater().download(AppLatestVersionDto(201, "2.0.1", server.url("/gastapp.apk").toString())) {}
            fail()
        } catch (e: IOException) {
            assertEquals("El servidor respondió 404.", e.message)
        }
    }

    @Test
    fun `buscar a mano avisa si ya esta al dia y abre el dialogo si hay version`() = runTest {
        val vm = UpdateViewModel(updater(), strings)
        assertEquals("Versión 2.0.0 (200)", vm.versionText(strings))

        server.enqueue(MockResponse().setBody(versionJson(200)))
        var message: String? = null
        vm.checkNow { message = it }
        vm.checking.first { !it }
        assertEquals("Ya tienes la versión más reciente.", message)

        server.enqueue(MockResponse().setBody(versionJson(201)))
        vm.checkNow { message = it }
        val shown = vm.state.first { it is UpdateUiState.Available } as UpdateUiState.Available
        assertEquals("2.0.201", shown.latest.versionName)

        vm.dismiss()
        assertEquals(UpdateUiState.Hidden, vm.state.value)

        server.enqueue(MockResponse().setResponseCode(500))
        vm.checkNow { message = it }
        vm.checking.first { !it }
        assertEquals("No se pudo buscar actualizaciones. Revisa tu conexión.", message)
    }
}
