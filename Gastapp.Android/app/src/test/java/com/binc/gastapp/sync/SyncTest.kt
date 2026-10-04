package com.binc.gastapp.sync

import com.binc.gastapp.core.remote.ApiClient
import com.binc.gastapp.core.remote.ApiJson
import com.binc.gastapp.core.remote.GastappApi
import com.binc.gastapp.core.remote.SyncDataDto
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.data.prefs.InMemoryDataStore
import com.binc.gastapp.data.prefs.RegisterDraftStore
import com.binc.gastapp.data.prefs.SessionStore
import com.binc.gastapp.data.session.SessionGuard
import com.binc.gastapp.data.session.SessionRepository
import java.time.Duration
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before

/**
 * Base de las pruebas de sesion y sincronizacion: Room en memoria (DbTest), el API real
 * de :core contra un MockWebServer y DataStore en memoria. El cliente espera 2 s, para
 * que un "timeout" no tarde los 120 s de produccion.
 */
abstract class SyncTest : DbTest() {
    protected lateinit var server: MockWebServer
    protected lateinit var api: GastappApi

    protected val sessionStore = SessionStore(InMemoryDataStore())
    protected val draftStore by lazy { RegisterDraftStore(InMemoryDataStore(), clock) }
    protected val guard by lazy { SessionGuard(sessionStore, clock) }
    protected val writer by lazy { LocalDataWriter(db, categories, clock) }
    protected val engine by lazy { SyncEngine(api, db, guard, sessionStore, writer, strings, clock) }
    protected val sessions by lazy { SessionRepository(api, db, sessionStore, draftStore, guard, writer, scheduler, strings, clock) }

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
        api = ApiClient.create(server.url("/api/").toString(), client)
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    /** Token vigente una semana. */
    protected suspend fun signedIn(token: String = "tok") = sessionStore.save(token, clock.instant().plus(Duration.ofDays(7)))

    protected fun respond(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    protected fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contratos/$name")) { "Falta el fixture $name" }.readText()

    protected fun RecordedRequest.syncBody(): SyncDataDto = ApiJson.decodeFromString(body.readUtf8())

    /** Todas las peticiones que llegaron, en orden ("POST /api/..."). */
    protected fun requests(): List<RecordedRequest> = List(server.requestCount) { server.takeRequest(1, TimeUnit.SECONDS)!! }
}
