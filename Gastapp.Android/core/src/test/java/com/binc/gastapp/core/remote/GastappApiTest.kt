package com.binc.gastapp.core.remote

import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El cliente Retrofit contra un servidor falso: rutas, parametros, token y errores. */
class GastappApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GastappApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build()
        api = ApiClient.create(server.url("/api/").toString(), client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login manda correo y contrasena en el cuerpo`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("login.json")))

        val result = apiCall { api.login(LoginRequest("ana@gastapp.dev", "secreta")) }

        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/User/Login", request.path)
        assertEquals("""{"email":"ana@gastapp.dev","password":"secreta"}""", request.body.readUtf8())
        assertNull("El login es anonimo", request.getHeader("Authorization"))
    }

    @Test
    fun `los parametros sueltos van en la query string como en Refit`() = runTest {
        server.enqueue(MockResponse().setBody("true"))
        server.enqueue(MockResponse().setBody("true"))
        server.enqueue(MockResponse().setBody("true"))

        api.verifyEmail("ana@gastapp.dev", "123456")
        api.confirmPasswordReset("ana@gastapp.dev", "654321", "n3w&pass")
        api.deleteCategory(bearer("tok"), "cat-1")

        val verify = server.takeRequest()
        assertEquals("/api/User/EmailVerification/verify?Email=ana%40gastapp.dev&Code=123456", verify.path)
        assertEquals("POST", verify.method)
        assertEquals(0L, verify.bodySize)

        val confirm = server.takeRequest()
        assertEquals("/api/User/PasswordReset/confirm?email=ana%40gastapp.dev&code=654321&newPassword=n3w%26pass", confirm.path)

        val delete = server.takeRequest()
        assertEquals("/api/Spendings/DeleteCategory?categoryId=cat-1", delete.path)
        assertEquals("Bearer tok", delete.getHeader("Authorization"))
    }

    @Test
    fun `SyncAllData lleva el token y responde true`() = runTest {
        server.enqueue(MockResponse().setBody("true"))

        val result = apiCall {
            api.syncAllData(bearer("tok"), SyncDataDto(categories = listOf(CategoryDto("c", "u", "Comida"))))
        }

        assertEquals(ApiResult.Success(true), result)
        val request = server.takeRequest()
        assertEquals("/api/Spendings/SyncAllData", request.path)
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun `un 400 trae el mensaje del API en texto plano o como string JSON`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Contraseña incorrecta. Verifica tu contraseña."))
        server.enqueue(MockResponse().setResponseCode(400).setBody("\"Email en uso\""))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"type":"https://tools.ietf.org/html/rfc9110#section-15.5.1","title":"One or more validation errors occurred."}"""))

        assertEquals(ApiResult.HttpError(400, "Contraseña incorrecta. Verifica tu contraseña."), apiCall { api.login(LoginRequest("a", "b")) })
        assertEquals(ApiResult.HttpError(400, "Email en uso"), apiCall { api.requestEmailVerification("a") })
        assertEquals(ApiResult.HttpError(400, null), apiCall { api.requestEmailVerification("a") })
    }

    @Test
    fun `401, 500 y timeout se distinguen`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500).setBody("Ocurrió un error al sincronizar los gastos."))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val unauthorized = apiCall { api.refreshToken(bearer("viejo")) }
        assertTrue(unauthorized is ApiResult.HttpError && unauthorized.isUnauthorized)

        val serverError = apiCall { api.getSpendings(bearer("tok")) }
        assertEquals(ApiResult.HttpError(500, "Ocurrió un error al sincronizar los gastos."), serverError)

        val timeout = apiCall { api.getSpendings(bearer("tok")) }
        assertTrue("Un timeout es error de red, no de sesion: $timeout", timeout is ApiResult.NetworkError)
    }

    @Test
    fun `una respuesta que no cumple el contrato no truena la app`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tokenValue":123}"""))
        server.enqueue(MockResponse().setBody("""[{"spendingId":"x"}]"""))

        assertTrue(apiCall { api.refreshToken(bearer("tok")) } is ApiResult.InvalidResponse)
        assertTrue(apiCall { api.getSpendings(bearer("tok")) } is ApiResult.InvalidResponse)
    }

    @Test
    fun `los gastos del servidor se leen con montos exactos`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("get_spendings.json")))

        val spendings = api.getSpendings(bearer("tok"))

        assertEquals(listOf(BigDecimal("123.45"), BigDecimal("45")), spendings.map { it.amount })
        assertEquals("GET", server.takeRequest().method)
    }
}
