package com.asu.project1

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(context: Context, factory: SQLiteDatabase.CursorFactory?) :
    SQLiteOpenHelper(context, DATABASE_NAME, factory, DATABASE_VERSION) {

    companion object{
        // here we have defined variables for our database

        // below is variable for database name
        private val DATABASE_NAME = "HealthCare.db"

        // below is the variable for database version
        private val DATABASE_VERSION = 1

        // below is the variable for table name
        val TABLE_NAME = "HealthCare"

        // below is the variable for id column
        val ID_COL = "id"

        // below is the variable for name column
        val Healthattribute = "Attributre"

        // below is the variable for age column
        val quant = "Value"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val query = ("CREATE TABLE " + TABLE_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY, " +
                Healthattribute + " Health Attributes," +
                quant + " VALUE" + ")")
        db?.execSQL(query)
    }

    override fun onUpgrade(db: SQLiteDatabase?, p1: Int, p2: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addName(attribute : String, val_ : String ){

        // below we are creating
        // a content values variable
        val values = ContentValues()

        // we are inserting our values
        // in the form of key-value pair
        values.put(Healthattribute, attribute)
        values.put(quant, val_)

        // here we are creating a
        // writable variable of
        // our database as we want to
        // insert value in our database
        val db = this.writableDatabase

        // all values are inserted into database
        db.insert(TABLE_NAME, null, values)

        // at last we are
        // closing our database
        db.close()
    }

    fun addData(data: Map<String, Float>) {
        val db = writableDatabase
        data.forEach { (attribute, value) ->
            val values = ContentValues()
            values.put(Healthattribute, attribute)
            values.put(quant, value.toString())
            db.insert(TABLE_NAME, null, values)
        }
        db.close()
    }
}