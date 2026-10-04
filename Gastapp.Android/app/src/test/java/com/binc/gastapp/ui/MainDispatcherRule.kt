package com.binc.gastapp.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Pone un despachador de prueba como Dispatchers.Main, que es donde corre viewModelScope.
 * runTest toma su reloj virtual, asi que lo que el ViewModel hace en Main corre en la
 * misma prueba.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/**
 * Espera a que el estado cumpla la condicion. Sin timeout propio a proposito: un
 * withTimeout corre con el reloj virtual de runTest y venceria en cuanto la prueba se
 * quedara esperando a la red (MockWebServer); el limite lo pone runTest en tiempo real.
 */
suspend fun <T> StateFlow<T>.awaitUntil(predicate: (T) -> Boolean): T = first(predicate)
