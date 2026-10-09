package com.example.upivoicealert

import android.content.Context
import androidx.room.*

@Entity(tableName = "payments")
data class PaymentRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: String,
    val upiApp: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface PaymentDao {
    @Insert
    suspend fun insertPayment(record: PaymentRecord)

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    suspend fun getAllPayments(): List<PaymentRecord>
}

@Database(entities = [PaymentRecord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upi_records.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
