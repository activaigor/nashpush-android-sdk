package com.nashpush.sample

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.*


class MainActivity : AppCompatActivity() {

    var tvLog: TextView? = null
    var locked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById<TextView>(R.id.tvLog).apply {
            movementMethod = ScrollingMovementMethod()
        }
        val btnSave = findViewById<Button>(R.id.btnSave)
        btnSave.setOnClickListener {
            val checkPermissions = checkPermissions(this)
            if (checkPermissions) {
                save()
            } else {
                getPermissions()
            }
        }
        val btnReload = findViewById<Button>(R.id.btnReload)
        btnReload.setOnClickListener { reloadLog() }
        reloadLog()


        val etToken = findViewById<EditText>(R.id.etToken)
        val token = App.settings?.getString("token", null)
        etToken.setText(token)
        val btnSaveToken = findViewById<Button>(R.id.btnSaveToken)
        btnSaveToken.setOnClickListener { saveToken(etToken) }
    }

    private fun saveToken(etToken: EditText?) {
        etToken?:return
        val settings = App.settings?:return
        val text = etToken.text.toString()
        val editor: SharedPreferences.Editor = settings.edit()
        editor.putString("token", text)
        editor.apply()

        finish()
    }

    private fun checkPermissions(context: Context): Boolean {
        neededPermissions().forEach {
            if (ContextCompat.checkSelfPermission(
                    context,
                    it
                ) == PackageManager.PERMISSION_DENIED
            ) {
                return false
            }
        }
        return true
    }

    private fun getPermissions() {
        permissionsLauncher.launch(neededPermissions())
    }

    fun neededPermissions(): Array<String> {
        val arrayListOf = arrayListOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )
        return arrayListOf.toTypedArray()
    }

    private fun reloadLog() {
        if (locked) {
            return
        }
        locked = true
        tvLog?.text = ""
        val log = StringBuilder()

        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            val bufferedReader = BufferedReader(
                InputStreamReader(process.inputStream)
            )
            var line: String? = ""
            while (bufferedReader.readLine().also { line = it } != null) {
                if (line?.contains("nashpush", true) == true) {
                    log.append(line)
                    log.append("\n")
                    log.append("\n")
                }
            }
        } catch (e: IOException) {
            // Handle Exception
        }

        tvLog?.text = log.toString()
        val scrollAmount = tvLog?.let {
            (it.layout?.getLineTop(it.lineCount) ?: 0) - it.height
        } ?: 0
        if (scrollAmount > 0)
            tvLog?.scrollTo(0, scrollAmount)
        else
            tvLog?.scrollTo(0, 0)
        locked = false
    }

    private var permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        var granted = true
        var rationale = true
        result.forEach {
            if (!it.value) {
                granted = false
                if (!shouldShowRequestPermissionRationale(it.key)){
                    rationale = false
                }
            }
        }
        if (granted) {
            save()
        } else {
            if (rationale) {
                Toast.makeText(this, "permissions needed", Toast.LENGTH_SHORT)
                    .show()
            } else{
                Toast.makeText(this, "go to settings for permission", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun save() {
        if (locked) {
            return
        }
        locked = true

        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            val bufferedReader = BufferedReader(
                InputStreamReader(process.inputStream)
            )
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            val fileWithinMyDir = File("$path/log.txt")
            if (fileWithinMyDir.exists()) {
                fileWithinMyDir.delete()
                fileWithinMyDir.createNewFile()
            } else{
                fileWithinMyDir.createNewFile()
            }
            var line: String? = ""
            val outputStreamWriter =
                OutputStreamWriter(FileOutputStream(fileWithinMyDir))
            while (bufferedReader.readLine().also { line = it } != null) {
                outputStreamWriter.write(line)
                outputStreamWriter.write("\n")
            }
            outputStreamWriter.close()
            Toast.makeText(this, "saved to downloads", Toast.LENGTH_SHORT)
                .show()

        } catch (e: IOException) {
            // Handle Exception
            Log.e("TestNashPush", e.message?:"error", e)
        }

        locked = false
    }
}