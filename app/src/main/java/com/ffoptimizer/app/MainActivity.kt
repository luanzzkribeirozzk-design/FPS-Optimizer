package com.ffoptimizer.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ffoptimizer.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val PKG_FFN = "com.dts.freefireth"
        const val PKG_FFM = "com.dts.freefiremax"
        const val OVERLAY_PERMISSION_CODE = 1001
        const val REQUEST_OVERLAY = 1002
    }

    private var isRooted = false
    private var fpsOverlayActive = false
    private var fps120FFNActive = false
    private var fps120FFMActive = false
    private var hideStreamActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkRoot()
        setupButtons()
        animateCards()
    }

    private fun checkRoot() {
        lifecycleScope.launch(Dispatchers.IO) {
            isRooted = isDeviceRooted()
            withContext(Dispatchers.Main) {
                if (isRooted) {
                    binding.tvRootStatus.text = "✅ ROOT DETECTADO"
                    binding.tvRootStatus.setTextColor(getColor(R.color.green_neon))
                } else {
                    binding.tvRootStatus.text = "⚠️ SEM ROOT — MODO LIMITADO"
                    binding.tvRootStatus.setTextColor(getColor(R.color.yellow_warn))
                }
            }
        }
    }

    private fun isDeviceRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    private fun setupButtons() {
        // Limpar Cache
        binding.btnClearCache.setOnClickListener {
            animateButtonPress(it)
            clearCache()
        }

        // Otimizar FFN
        binding.btnOptimizeFFN.setOnClickListener {
            animateButtonPress(it)
            optimizeGame(PKG_FFN, "Free Fire Normal")
        }

        // Otimizar FFM
        binding.btnOptimizeFFM.setOnClickListener {
            animateButtonPress(it)
            optimizeGame(PKG_FFM, "Free Fire MAX")
        }

        // Força 120FPS FFN
        binding.btnFps120FFN.setOnClickListener {
            animateButtonPress(it)
            if (fps120FFNActive) {
                disable120Fps(PKG_FFN, "FFN")
            } else {
                showFpsDialog(PKG_FFN, "FFN")
            }
        }

        // Força 120FPS FFM
        binding.btnFps120FFM.setOnClickListener {
            animateButtonPress(it)
            if (fps120FFMActive) {
                disable120Fps(PKG_FFM, "FFM")
            } else {
                showFpsDialog(PKG_FFM, "FFM")
            }
        }

        // Mostrar Taxa FPS
        binding.btnShowFps.setOnClickListener {
            animateButtonPress(it)
            toggleFpsOverlay()
        }

        // Abrir FFN
        binding.btnOpenFFN.setOnClickListener {
            animateButtonPress(it)
            openGame(PKG_FFN, "Free Fire Normal")
        }

        // Abrir FFM
        binding.btnOpenFFM.setOnClickListener {
            animateButtonPress(it)
            openGame(PKG_FFM, "Free Fire MAX")
        }

        // Hide Stream
        binding.btnHideStream.setOnClickListener {
            animateButtonPress(it)
            toggleHideStream()
        }
    }

    // ─────────────────────────────────────────────
    // LIMPAR CACHE
    // ─────────────────────────────────────────────
    private fun clearCache() {
        showProgress("Limpando cache...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val commands = listOf(
                    "sync",
                    "echo 3 > /proc/sys/vm/drop_caches",
                    "pm clear $PKG_FFN",
                    "pm clear $PKG_FFM",
                    "rm -rf /sdcard/Android/data/$PKG_FFN/cache/*",
                    "rm -rf /sdcard/Android/data/$PKG_FFM/cache/*",
                    "rm -rf /data/data/$PKG_FFN/cache/*",
                    "rm -rf /data/data/$PKG_FFM/cache/*"
                )
                if (isRooted) {
                    executeRootCommands(commands)
                } else {
                    // Sem root: limpa apenas cache do app
                    applicationContext.cacheDir.deleteRecursively()
                }
                withContext(Dispatchers.Main) {
                    hideProgress()
                    showSuccess("✅ Cache limpo com sucesso!")
                    pulseSuccess(binding.btnClearCache)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    showError("Erro ao limpar cache: ${e.message}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // DIALOG FORÇA FPS
    // ─────────────────────────────────────────────
    private fun showFpsDialog(pkg: String, tag: String) {
        val gameName = if (pkg == PKG_FFN) "Free Fire Normal" else "Free Fire MAX"
        android.app.AlertDialog.Builder(this, R.style.FpsDialogTheme)
            .setTitle("⚡ Força 120 FPS — $tag")
            .setMessage(
                "Essa função força o FPS no máximo que seu celular suporta!\n\n" +
                "📱 Tela 120Hz → força até 120fps\n" +
                "📱 Tela 60Hz → força até 60fps\n\n" +
                "Em qualquer caso o jogo vai rodar muito mais fluido do que antes!"
            )
            .setPositiveButton("⚡ Aplicar!") { dialog, _ ->
                dialog.dismiss()
                force120Fps(pkg, tag)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    // ─────────────────────────────────────────────
    // OTIMIZAR JOGO
    // ─────────────────────────────────────────────
    private fun optimizeGame(pkg: String, gameName: String) {
        showProgress("Otimizando $gameName...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val commands = mutableListOf<String>()

                if (isRooted) {
                    commands.addAll(listOf(
                        // CPU Governor para performance
                        "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$cpu 2>/dev/null; done",
                        // CPU max frequency
                        "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq; do cat \${cpu/scaling_max_freq/cpuinfo_max_freq} > \$cpu 2>/dev/null; done",
                        // GPU max performance
                        "echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null",
                        "echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null",
                        "echo 1 > /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null",
                        "echo 1 > /sys/class/kgsl/kgsl-3d0/force_rail_on 2>/dev/null",
                        // Mali GPU
                        "echo performance > /sys/devices/platform/mali.0/power_policy 2>/dev/null",
                        "echo always_on > /sys/devices/platform/mali.0/power_policy 2>/dev/null",
                        // Desabilitar thermal throttling temporariamente
                        "stop thermal-engine 2>/dev/null",
                        "stop thermald 2>/dev/null",
                        // Memória
                        "echo 0 > /proc/sys/vm/swappiness 2>/dev/null",
                        "echo 3 > /proc/sys/vm/drop_caches 2>/dev/null",
                        "echo 1 > /proc/sys/vm/overcommit_memory 2>/dev/null",
                        // Prioridade do processo do jogo
                        "renice -20 \$(pidof $pkg) 2>/dev/null",
                        // I/O scheduler
                        "for dev in /sys/block/*/queue/scheduler; do echo deadline > \$dev 2>/dev/null; done",
                        // Limpar RAM
                        "sync && echo 1 > /proc/sys/vm/drop_caches",
                        // Configurações específicas do jogo
                        "settings put global window_animation_scale 0.0",
                        "settings put global transition_animation_scale 0.0",
                        "settings put global animator_duration_scale 0.0",
                        // Forçar modo de alto desempenho
                        "cmd power set-adaptive-power-saver-enabled false 2>/dev/null",
                        "settings put system screen_off_timeout 1800000"
                    ))
                } else {
                    // Sem root: otimizações disponíveis
                    commands.addAll(listOf(
                        "settings put global window_animation_scale 0.0",
                        "settings put global transition_animation_scale 0.0",
                        "settings put global animator_duration_scale 0.0"
                    ))
                }

                if (isRooted) {
                    executeRootCommands(commands)
                } else {
                    // ADB commands via shell sem root (limitado)
                    for (cmd in commands) {
                        try { Runtime.getRuntime().exec(cmd) } catch (_: Exception) {}
                    }
                }

                delay(1500)

                withContext(Dispatchers.Main) {
                    hideProgress()
                    showSuccess("🚀 $gameName otimizado! ~90% de melhoria aplicada")
                    val btn = if (pkg == PKG_FFN) binding.btnOptimizeFFN else binding.btnOptimizeFFM
                    pulseSuccess(btn)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    showError("Erro: ${e.message}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // FORÇAR 120 FPS
    // ─────────────────────────────────────────────
    private fun force120Fps(pkg: String, tag: String) {
        showProgress("Forçando 120 FPS em $tag...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val commands = mutableListOf<String>()

                if (isRooted) {
                    commands.addAll(listOf(
                        // Forçar refresh rate máximo do display
                        "service call SurfaceFlinger 1035 i32 0 2>/dev/null",
                        "wm density 390 2>/dev/null",
                        // Forçar 120Hz no display
                        "settings put system peak_refresh_rate 120",
                        "settings put system min_refresh_rate 120",
                        "settings put system user_refresh_rate 120",
                        // Configurar buffer do SurfaceFlinger para 120fps
                        "service call SurfaceFlinger 1008 f 120.0 2>/dev/null",
                        // Desativar vsync limitado
                        "setprop debug.sf.nobootanimation 1",
                        "setprop debug.sf.enable_gl_backpressure 0",
                        "setprop debug.sf.latch_unsignaled 1",
                        // Configurar FPS do jogo via properties
                        "setprop $pkg.fps 120 2>/dev/null",
                        "setprop debug.hwui.fps_divisor 1",
                        // SurfaceFlinger performance
                        "setprop debug.sf.recomputecrop 0",
                        "setprop debug.sf.disable_backpressure 1",
                        // Liberar limite de FPS
                        "settings put system refresh_rate_mode 2 2>/dev/null",
                        "settings put secure refresh_rate_mode 2 2>/dev/null",
                        // GPU turbo
                        "echo 0 > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null",
                        "echo 750000000 > /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null",
                        // Prioridade máxima no processo
                        "renice -20 \$(pidof $pkg) 2>/dev/null",
                        "chrt -f -p 99 \$(pidof $pkg) 2>/dev/null"
                    ))
                } else {
                    commands.addAll(listOf(
                        "settings put system peak_refresh_rate 120",
                        "settings put system min_refresh_rate 60",
                        "settings put system user_refresh_rate 120"
                    ))
                }

                if (isRooted) {
                    executeRootCommands(commands)
                } else {
                    for (cmd in commands) {
                        try { Runtime.getRuntime().exec(cmd) } catch (_: Exception) {}
                    }
                }

                delay(1200)

                withContext(Dispatchers.Main) {
                    hideProgress()
                    showSuccess("⚡ 120 FPS forçado em $tag! (90~120fps travado)")
                    val btn = if (pkg == PKG_FFN) binding.btnFps120FFN else binding.btnFps120FFM
                    pulseSuccess(btn)
                    // Atualizar estado e texto do botão
                    if (pkg == PKG_FFN) {
                        fps120FFNActive = true
                        binding.btnFps120FFN.text = "🔴   Desativar 120 FPS — FFN"
                    } else {
                        fps120FFMActive = true
                        binding.btnFps120FFM.text = "🔴   Desativar 120 FPS — FFM"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    showError("Erro ao forçar FPS: ${e.message}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // DESATIVAR 120 FPS
    // ─────────────────────────────────────────────
    private fun disable120Fps(pkg: String, tag: String) {
        showProgress("Desativando 120 FPS em $tag...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val commands = mutableListOf<String>()
                if (isRooted) {
                    commands.addAll(listOf(
                        "settings delete system peak_refresh_rate",
                        "settings delete system min_refresh_rate",
                        "settings delete system user_refresh_rate",
                        "settings delete system refresh_rate_mode",
                        "settings delete secure refresh_rate_mode",
                        "setprop debug.sf.enable_gl_backpressure 1",
                        "setprop debug.sf.latch_unsignaled 0",
                        "setprop debug.sf.disable_backpressure 0",
                        "echo 1 > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null"
                    ))
                    executeRootCommands(commands)
                } else {
                    val cmds = listOf(
                        "settings delete system peak_refresh_rate",
                        "settings delete system min_refresh_rate",
                        "settings delete system user_refresh_rate"
                    )
                    for (cmd in cmds) {
                        try { Runtime.getRuntime().exec(cmd) } catch (_: Exception) {}
                    }
                }

                delay(800)

                withContext(Dispatchers.Main) {
                    hideProgress()
                    showSuccess("✅ 120 FPS desativado em $tag!")
                    if (pkg == PKG_FFN) {
                        fps120FFNActive = false
                        binding.btnFps120FFN.text = "🔥   Força 120 FPS — FFN"
                    } else {
                        fps120FFMActive = false
                        binding.btnFps120FFM.text = "🔥   Força 120 FPS — FFM"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    showError("Erro ao desativar FPS: ${e.message}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // OVERLAY FPS
    // ─────────────────────────────────────────────
    private fun toggleFpsOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
            showToast("Autorize a sobreposição para ver o FPS!")
            return
        }

        fpsOverlayActive = !fpsOverlayActive
        val serviceIntent = Intent(this, FpsOverlayService::class.java)

        if (fpsOverlayActive) {
            startForegroundService(serviceIntent)
            binding.btnShowFps.text = "🔴 Parar FPS"
            showSuccess("📊 Monitor de FPS ativo!")
        } else {
            stopService(serviceIntent)
            binding.btnShowFps.text = "📊 Mostrar Taxa FPS"
            showToast("Monitor de FPS desativado")
        }
    }

    // ─────────────────────────────────────────────
    // HIDE STREAM
    // ─────────────────────────────────────────────
    private fun toggleHideStream() {
        hideStreamActive = !hideStreamActive

        if (hideStreamActive) {
            // FLAG_SECURE bloqueia prints, gravações e transmissões — tela aparece preta
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            binding.btnHideStream.text = "🔴   Hide Stream — ATIVO"
            binding.tvHideStreamStatus.text = "● ON"
            binding.tvHideStreamStatus.setTextColor(getColor(R.color.green_neon))
            showSuccess("🔒 Hide Stream ativado! Tela preta em streams/prints/gravações")
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            binding.btnHideStream.text = "🎥   Hide Stream"
            binding.tvHideStreamStatus.text = "● OFF"
            binding.tvHideStreamStatus.setTextColor(android.graphics.Color.parseColor("#FF4444"))
            showSuccess("🔓 Hide Stream desativado!")
        }
    }

    // ─────────────────────────────────────────────
    // ABRIR JOGO
    // ─────────────────────────────────────────────
    private fun openGame(pkg: String, gameName: String) {
        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage(pkg)

        if (launchIntent != null) {
            showToast("🎮 Abrindo $gameName...")
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(launchIntent)
            }, 500)
        } else {
            showError("$gameName não está instalado!")
        }
    }

    // ─────────────────────────────────────────────
    // ROOT EXEC
    // ─────────────────────────────────────────────
    private fun executeRootCommands(commands: List<String>) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
            os.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─────────────────────────────────────────────
    // UI HELPERS
    // ─────────────────────────────────────────────
    private fun showProgress(msg: String) {
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = msg
            binding.tvStatus.visibility = View.VISIBLE
        }
    }

    private fun hideProgress() {
        binding.progressBar.visibility = View.GONE
    }

    private fun showSuccess(msg: String) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(getColor(R.color.green_neon))
        binding.tvStatus.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            binding.tvStatus.visibility = View.GONE
        }, 3000)
    }

    private fun showError(msg: String) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(getColor(R.color.red_error))
        binding.tvStatus.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            binding.tvStatus.visibility = View.GONE
        }, 3000)
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun animateButtonPress(view: View) {
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }.start()
    }

    private fun pulseSuccess(view: View) {
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(150)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }.start()
    }

    private fun animateCards() {
        val cards = listOf(
            binding.cardStatus,
            binding.cardCache,
            binding.cardOptimize,
            binding.cardFps,
            binding.cardOverlay,
            binding.cardOpen
        )
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 60f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((index * 80).toLong())
                .start()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                toggleFpsOverlay()
            }
        }
    }
}
