package com.madinatent.service77f

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String = ""
)

@Entity
data class Stock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val totalQty: Int,
    val rent: Int
)

@Entity(
    foreignKeys = [ForeignKey(entity = Customer::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("customerId")]
)
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val date: String,
    val total: Int,
    val advance: Int
)

@Entity(
    foreignKeys = [ForeignKey(entity = Booking::class, parentColumns = ["id"], childColumns = ["bookingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookingId"), Index("stockId")]
)
data class BookingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long,
    val stockId: Long,
    val qty: Int,
    val rent: Int
)

@Entity(
    foreignKeys = [ForeignKey(entity = Booking::class, parentColumns = ["id"], childColumns = ["bookingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookingId")]
)
data class ReturnItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long,
    val stockId: Long,
    val returnedQty: Int,
    val missingQty: Int,
    val damagedQty: Int,
    val date: String
)

@Entity(
    foreignKeys = [ForeignKey(entity = Booking::class, parentColumns = ["id"], childColumns = ["bookingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookingId")]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long,
    val amount: Int,
    val date: String,
    val note: String = ""
)

@Dao
interface CustomerDao {
    @Query("SELECT * FROM Customer ORDER BY id DESC") fun all(): Flow<List<Customer>>
    @Insert suspend fun insert(c: Customer): Long
    @Query("SELECT * FROM Customer WHERE id=:id LIMIT 1") suspend fun get(id: Long): Customer?
}

@Dao
interface StockDao {
    @Query("SELECT * FROM Stock ORDER BY id") fun all(): Flow<List<Stock>>
    @Query("SELECT * FROM Stock ORDER BY id") suspend fun allOnce(): List<Stock>
    @Insert suspend fun insertAll(s: List<Stock>)
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM Booking ORDER BY id DESC") fun all(): Flow<List<Booking>>
    @Insert suspend fun insert(b: Booking): Long
    @Query("SELECT * FROM Booking WHERE id=:id LIMIT 1") suspend fun get(id: Long): Booking?
    @Query("DELETE FROM Booking") suspend fun clear()
}

@Dao
interface BookingItemDao {
    @Query("SELECT * FROM BookingItem WHERE bookingId=:id") suspend fun byBooking(id: Long): List<BookingItem>
    @Query("SELECT * FROM BookingItem") suspend fun allOnce(): List<BookingItem>
    @Insert suspend fun insertAll(items: List<BookingItem>)
    @Query("DELETE FROM BookingItem") suspend fun clear()
}

@Dao
interface ReturnDao {
    @Query("SELECT * FROM ReturnItem") suspend fun allOnce(): List<ReturnItem>
    @Query("SELECT * FROM ReturnItem WHERE bookingId=:bookingId AND stockId=:stockId") suspend fun forItem(bookingId: Long, stockId: Long): List<ReturnItem>
    @Insert suspend fun insertAll(items: List<ReturnItem>)
    @Query("DELETE FROM ReturnItem") suspend fun clear()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM Payment") suspend fun allOnce(): List<Payment>
    @Query("SELECT COALESCE(SUM(amount),0) FROM Payment WHERE bookingId=:bookingId") suspend fun paid(bookingId: Long): Int
    @Insert suspend fun insert(p: Payment)
    @Query("DELETE FROM Payment") suspend fun clear()
}

@Database(entities=[Customer::class, Stock::class, Booking::class, BookingItem::class, ReturnItem::class, Payment::class], version=1, exportSchema=false)
abstract class AppDb: RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun stockDao(): StockDao
    abstract fun bookingDao(): BookingDao
    abstract fun bookingItemDao(): BookingItemDao
    abstract fun returnDao(): ReturnDao
    abstract fun paymentDao(): PaymentDao
    companion object {
        fun create(ctx: android.content.Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "madina_tent_service.db").build()
    }
}