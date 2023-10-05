package com.asu.project1

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.ui.AppBarConfiguration
import com.asu.project1.R
import com.asu.project1.databinding.ActivitySymptomBinding


import java.io.File
import java.io.IOException

class SymptomActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivitySymptomBinding
    private var symptom: String = "Select Symptoms"
    private var map = mutableMapOf<String, Float>()
    private val STORAGE_PERMISSION_REQUEST_CODE = 0

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySymptomBinding.inflate(layoutInflater)
        setContentView(binding.root)//
        var heartRate = intent.getStringExtra("heartRate")
//        var respiratoryRate = deserializeToMap(intent.getStringExtra("respRate")!!)
        var respiratoryRate = intent.getStringExtra("respRate")

        map.put("heartRate",heartRate?.toFloat()!!)
        map.put("respiratoryRate",respiratoryRate?.toFloat()!!)

        var symptomsArray = resources.getStringArray(R.array.symptoms)

        //access the tablelayout
        val tableLayout = findViewById<TableLayout>(R.id.tableLayout1)
        for (symp in symptomsArray) {

            if (symp == "Select Symptoms") {
                continue
            }

                    map[symp] = 0.0f
        }



        binding.btnSave.setOnClickListener {
            val db = DBHelper(this, null)
            db.addData(map)
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted, so request it from the user
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
                val data = retrieveDataForAnalysis(this)
                println("data fromdb"+data)
                //add contet to tablelayout
                for (symp in data) {

                    if (symp.first == "Select Symptoms") {
                        continue
                    }

//                    map[symp] = 0.0f

                    val newRow = TableRow(this)

                    val textView1 = TextView(this)
                    textView1.text = symp.first
                    textView1.layoutParams = TableRow.LayoutParams(
                        TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT
                    )

                    val textView2 = TextView(this)
                    textView2.text = symp.second.toString()
                    textView2.layoutParams = TableRow.LayoutParams(
                        TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT
                    )

                    newRow.addView(textView1)
                    newRow.addView(textView2)
                    tableLayout.addView(newRow)

                }

            } else {
                println("no db permission")
                exportDataToCsv(this)
            }
        }

        //set spinner
        val spinner: Spinner = findViewById(R.id.spinnerHealthSymptoms)
        ArrayAdapter.createFromResource(
            this,
            R.array.symptoms,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        //set ratings
        binding.ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            if (symptom != "Select Symptoms") {
                map[symptom] = rating

            }
        }
        //set spinner listener
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //do nothing
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                symptom = parent?.getItemAtPosition(position).toString()
                binding.ratingBar.rating = 0.0f
                if (symptom != "Select Symptoms") {
                    map[symptom] = 0.0f
                }
            }
        }
    }

    @SuppressLint("Range")
    private fun retrieveDataForAnalysis(context: Context): List<Pair<String, Float>> {
        val data = mutableListOf<Pair<String, Float>>()
        val db = DBHelper(context, null).readableDatabase
        val columns = arrayOf(DBHelper.Healthattribute, DBHelper.quant)
        val cursor: Cursor = db.query(
            DBHelper.TABLE_NAME,
            columns,
            null,
            null,
            null,
            null,
            null
        )

        while (cursor.moveToNext()) {
            val attribute = cursor.getString(cursor.getColumnIndex(DBHelper.Healthattribute))
            val value = cursor.getFloat(cursor.getColumnIndex(DBHelper.quant))
            data.add(Pair(attribute, value))
        }

        //clear the db
        db.delete(DBHelper.TABLE_NAME, null, null)
        cursor.close()
        db.close()

        return data
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveDataToCsv(context: Context, data: List<Pair<String, Float>>, fileName: String) {
        val basePath = "/storage/emulated/0/Android/media/com.example.videocapture45/data"
        val dir = File(basePath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val filePath = File(dir, fileName)

        try {
            Toast.makeText(context, "Data exported to $filePath", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun deserializeToMap(serializedString: String): Map<String, Float> {
        // Remove curly braces and split the string into key-value pairs
        val keyValuePairs = serializedString
            .removeSurrounding("{", "}")
            .split(", ")
            .map { it.split("=") }

        // Create a Map and populate it with the key-value pairs
        val map = mutableMapOf<String, Float>()
        for (pair in keyValuePairs) {
            if (pair.size == 2) {
                val key = pair[0].trim()
                val value = pair[1].trim()
                map[key] = value.toFloat()
            }
        }

        return map
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportDataToCsv(context: Context) {
        val data = retrieveDataForAnalysis(context)
        println("data fromdb"+data)
        saveDataToCsv(context, data, "HealthCare.csv")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted, proceed with exporting data to CSV
                exportDataToCsv(this)
            } else {
                // Permission is denied, handle it accordingly (e.g., show a message)
            }
        }
    }

}