package com.example.upivoicealert

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PaymentRecord(
    val id: Long = 0,
    val amount: String,
    val upiApp: String,
    val timestamp: Long
)

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "upi_payments.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE payments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "amount TEXT, " +
                    "upiApp TEXT, " +
                    "timestamp INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS payments")
        onCreate(db)
    }

    fun insertPayment(amount: String, upiApp: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("amount", amount)
            put("upiApp", upiApp)
            put("timestamp", System.currentTimeMillis())
        }
        db.insert("payments", null, values)
    }

    fun getAllPayments(): List<PaymentRecord> {
        val list = mutableListOf<PaymentRecord>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM payments ORDER BY id DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val record = PaymentRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    amount = cursor.getString(cursor.getColumnIndexOrThrow("amount")),
                    upiApp = cursor.getString(cursor.getColumnIndexOrThrow("upiApp")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                )
                list.add(record)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
