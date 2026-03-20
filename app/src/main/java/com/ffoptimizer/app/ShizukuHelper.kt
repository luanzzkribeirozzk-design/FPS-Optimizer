package com.ffoptimizer.app

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

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
            Log.e(TAG, "Erro permissao: ${e.message}")
        }
    }

    fun exec(command: String): ShizukuResult {
        return try {
            if (!isAvailable()) return ShizukuResult(false, "", "Shizuku indisponivel")
            if (!isGranted()) return ShizukuResult(false, "", "Permissao negada")

            // Usar Runtime direto via shell — Shizuku eleva os privilegios do processo
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            ShizukuResult(true, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            ShizukuResult(false, "", e.message ?: "Erro desconhecido")
        }
    }

    data class ShizukuResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String
    )
}
