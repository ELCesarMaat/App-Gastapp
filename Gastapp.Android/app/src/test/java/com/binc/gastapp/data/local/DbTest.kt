package com.binc.gastapp.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.data.repository.SubscriptionRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.sync.SyncScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Cuenta las veces que los repositorios piden sincronizar. */
class RecordingSyncScheduler : SyncScheduler {
    var requests = 0
    override fun requestSync() {
        requests++
    }
}

/**
 * Base de las pruebas de Room: base en memoria (con las llaves foraneas activas, como en
 * la app), reloj fijo el 2 oct 2026 a mediodia en Ciudad de Mexico y los repositorios
 * armados a mano.
 */
@RunWith(RobolectricTestRunner::class)
abstract class DbTest {
    protected lateinit var db: GastappDatabase
    protected val zone: ZoneId = ZoneId.of("America/Mexico_City")
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-10-02T18:00:00Z"), zone)
    protected val today: LocalDate = LocalDate.of(2026, 10, 2)
    protected val scheduler = RecordingSyncScheduler()

    protected val users by lazy { UserRepository(db, scheduler) }
    protected val categories by lazy { CategoryRepository(db, users, scheduler) }
    protected val spendings by lazy { SpendingRepository(db, users, categories, scheduler, clock) }
    protected val cards by lazy { CreditCardRepository(db, users, categories, spendings, scheduler, clock) }
    protected val subscriptions by lazy { SubscriptionRepository(db, users, categories, spendings, scheduler, clock) }

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GastappDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ------------------------------------------------------------- datos de prueba

    protected val userId = "user-1"

    /** Usuario quincenal (1 y 16) con "Sin categoria", "Comida" y una tarjeta. */
    protected suspend fun seedBasics() {
        db.incomeTypeDao().upsertAll(listOf(IncomeTypeEntity(1, "Semanal"), IncomeTypeEntity(2, "Quincenal"), IncomeTypeEntity(3, "Mensual")))
        db.userDao().upsert(
            UserEntity(
                userId = userId,
                name = "Prueba",
                email = "prueba@example.com",
                salaryCents = 1_000_000,
                percentSave = BigDecimal("15.3846"),
                birthDate = LocalDate.of(1990, 1, 31),
                incomeTypeId = 2,
                firstPayDay = 1,
                secondPayDay = 16,
                weekPayDay = null,
                isSynced = true,
            ),
        )
        db.categoryDao().upsertAll(
            listOf(
                CategoryEntity("cat-default", userId, "Sin categoria", isDefaultCategory = true, isSynced = true),
                CategoryEntity("cat-food", userId, "Comida", isSynced = true),
            ),
        )
        db.creditCardDao().upsert(card("card-1"))
    }

    protected fun card(id: String, cutOffDay: Int = 5, paymentDay: Int = 25) = CreditCardEntity(
        creditCardId = id,
        userId = userId,
        cardName = "Tarjeta $id",
        bankName = "Banco",
        lastFourDigits = "1234",
        cutOffDay = cutOffDay,
        paymentDay = paymentDay,
        creditLimitCents = 2_000_000,
        isSynced = true,
    )

    protected fun spending(
        id: String,
        cents: Long,
        date: LocalDateTime,
        categoryId: String = "cat-food",
        isCreditCard: Boolean = false,
        creditCardId: String? = null,
        isDeleted: Boolean = false,
        isSynced: Boolean = true,
        deletedAt: Instant? = null,
    ) = SpendingEntity(
        spendingId = id,
        userId = userId,
        categoryId = categoryId,
        title = "Gasto $id",
        description = null,
        amountCents = cents,
        date = date,
        isCreditCard = isCreditCard,
        creditCardId = creditCardId,
        isSynced = isSynced,
        isDeleted = isDeleted,
        deletedAt = deletedAt,
    )

    protected fun subscription(
        id: String,
        creditCardId: String? = null,
        categoryId: String? = null,
        isDeleted: Boolean = false,
        isSynced: Boolean = true,
        deletedAt: Instant? = null,
    ) = SubscriptionEntity(
        subscriptionId = id,
        userId = userId,
        serviceName = "Servicio $id",
        amountCents = 19_900,
        firstChargeDate = LocalDate.of(2026, 1, 31),
        creditCardId = creditCardId,
        categoryId = categoryId,
        isSynced = isSynced,
        isDeleted = isDeleted,
        deletedAt = deletedAt,
    )

    protected fun at(day: LocalDate, hour: Int, minute: Int = 0, second: Int = 0, nanos: Int = 0): LocalDateTime =
        day.atTime(hour, minute, second, nanos)
}
