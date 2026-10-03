package com.binc.gastapp.data.local

import android.database.sqlite.SQLiteConstraintException
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las llaves foraneas de OnModelCreating (MAUI), comprobadas contra SQLite. */
class ForeignKeysTest : DbTest() {

    @Test
    fun borrarElUsuarioBorraTodoLoSuyo() = runTest {
        seedBasics()
        db.spendingDao().upsert(spending("s1", 100, at(today, 9), isCreditCard = true, creditCardId = "card-1"))
        db.subscriptionDao().upsert(subscription("sub-1", creditCardId = "card-1", categoryId = "cat-food"))

        db.openHelper.writableDatabase.execSQL("DELETE FROM users")

        assertTrue(db.categoryDao().getAll().isEmpty())
        assertTrue(db.creditCardDao().getAll().isEmpty())
        assertNull(db.spendingDao().get("s1"))
        assertNull(db.subscriptionDao().get("sub-1"))
    }

    @Test
    fun borrarUnaCategoriaArrastraSusGastosYSoltaLaSuscripcion() = runTest {
        seedBasics()
        db.spendingDao().upsert(spending("s1", 100, at(today, 9), categoryId = "cat-food"))
        db.subscriptionDao().upsert(subscription("sub-1", categoryId = "cat-food"))

        db.categoryDao().delete("cat-food")

        assertNull(db.spendingDao().get("s1"))
        val sub = db.subscriptionDao().get("sub-1")
        assertNotNull(sub)
        assertNull(sub!!.categoryId)
    }

    @Test
    fun borrarUnaTarjetaNoBorraLaSuscripcion() = runTest {
        seedBasics()
        db.subscriptionDao().upsert(subscription("sub-1", creditCardId = "card-1"))

        db.openHelper.writableDatabase.execSQL("DELETE FROM credit_cards WHERE creditCardId = 'card-1'")

        assertNull(db.subscriptionDao().get("sub-1")!!.creditCardId)
    }

    @Test
    fun unGastoNoDejaBorrarSuTarjeta() = runTest {
        seedBasics()
        db.spendingDao().upsert(spending("s1", 100, at(today, 9), isCreditCard = true, creditCardId = "card-1"))

        assertThrows(SQLiteConstraintException::class.java) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM credit_cards WHERE creditCardId = 'card-1'")
        }
    }

    /** Regla 12: quien inserte datos del servidor tiene que anular antes las referencias colgantes. */
    @Test
    fun unaReferenciaColganteTruenaLaInsercion() = runTest {
        seedBasics()
        assertTrue(failsWithConstraint { db.spendingDao().upsert(spending("s1", 100, at(today, 9), isCreditCard = true, creditCardId = "no-existe")) })
        assertTrue(failsWithConstraint { db.subscriptionDao().upsert(subscription("sub-1", categoryId = "no-existe")) })
    }

    private suspend fun failsWithConstraint(block: suspend () -> Unit): Boolean = try {
        block()
        false
    } catch (_: SQLiteConstraintException) {
        true
    }

    @Test
    fun laPurgaSoloBorraTarjetasQueYaNadieUsa() = runTest {
        seedBasics()
        val hace31 = clock.instant().minus(Duration.ofDays(31))
        db.creditCardDao().upsertAll(
            listOf(
                card("libre").copy(isDeleted = true, deletedAt = hace31),
                card("conGasto").copy(isDeleted = true, deletedAt = hace31),
                card("conSuscripcion").copy(isDeleted = true, deletedAt = hace31),
                // El gasto que la referencia tambien se purga en la misma pasada.
                card("conGastoViejo").copy(isDeleted = true, deletedAt = hace31),
            ),
        )
        db.spendingDao().upsertAll(
            listOf(
                spending("vivo", 100, at(today, 9), isCreditCard = true, creditCardId = "conGasto"),
                spending("viejo", 100, at(today, 9), isCreditCard = true, creditCardId = "conGastoViejo", isDeleted = true, deletedAt = hace31),
            ),
        )
        db.subscriptionDao().upsert(subscription("sub-1", creditCardId = "conSuscripcion"))

        val resultado = LocalPurge(db, clock).purge()

        assertEquals(LocalPurge.Result(spendings = 1, subscriptions = 0, cards = 2), resultado)
        assertNull(db.creditCardDao().get("libre"))
        assertNull(db.creditCardDao().get("conGastoViejo"))
        assertNotNull(db.creditCardDao().get("conGasto"))
        assertNotNull(db.creditCardDao().get("conSuscripcion"))
    }
}
