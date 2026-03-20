package com.ffoptimizer.app

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val REQUEST_CODE = 1010

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun isGranted(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar permissao Shizuku: ${e.message}")
        }
    }

    fun exec(command: String): ShizukuResult {
        return try {
            if (!isAvailable()) return ShizukuResult(false, "", "Shizuku não está rodando")
            if (!isGranted()) return ShizukuResult(false, "", "Permissão Shizuku negada")

            val process: ShizukuRemoteProcess = Shizuku.newProcess(
                arrayOf("sh", "-c", command), null, null
            )
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            ShizukuResult(true, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            ShizukuResult(false, "", e.message ?: "Erro desconhecido")
        }
    }

    fun execMultiple(commands: List<String>): ShizukuResult {
        val combined = commands.joinToString(" && ")
        return exec(combined)
    }

    data class ShizukuResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String
    )
}
