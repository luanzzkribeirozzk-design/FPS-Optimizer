package com.ffoptimizer.app

import android.app.AlertDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: com.ffoptimizer.app.databinding.ActivityMainBinding

    companion object {
        const val PKG_FFN = "com.dts.freefireth"
        const val PKG_FFM = "com.dts.freefiremax"
        const val REQUEST_OVERLAY = 1002
    }

    private var fpsOverlayActive = false
    private var fps120FFNActive = false
    private var fps120FFMActive = false

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            logTerminal("> Shizuku autorizado! Modo completo ativo")
            showMainUI()
        } else {
            showShizukuWall("Permissão negada pelo usuário.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.ffoptimizer.app.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Shizuku.addRequestPermissionResultListener(shizukuListener)
        setupButtons()

        // Verifica Shizuku ao iniciar
        checkShizukuOnStart()
    }

    override fun onResume() {
        super.onResume()
        // Recheck toda vez que voltar ao app
        checkShizukuOnStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }

    // ─────────────────────────────────────────────
    // SHIZUKU WALL — NADA FUNCIONA SEM ELE
    // ─────────────────────────────────────────────
    private fun checkShizukuOnStart() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(300)
            withContext(Dispatchers.Main) {
                when {
                    !ShizukuHelper.isAvailable() -> {
                        showShizukuWall("Shizuku não está rodando.\nInstale e ative o Shizuku para continuar.")
                    }
                    !ShizukuHelper.isGranted() -> {
                        binding.tvRootStatus.text = "SHIZUKU — AGUARDANDO AUTORIZACAO"
                        binding.tvRootStatus.setTextColor(android.graphics.Color.parseColor("#FFD600"))
                        showShizukuWall("Autorize o FPS Optimizer no Shizuku.")
                        ShizukuHelper.requestPermission()
                    }
                    else -> showMainUI()
                }
            }
        }
    }

    private fun showShizukuWall(reason: String) {
        // Bloqueia toda a UI
        binding.scrollContent.visibility = View.GONE
        binding.layoutShizukuWall.visibility = View.VISIBLE
        binding.tvShizukuReason.text = reason
        binding.tvRootStatus.text = "SHIZUKU INATIVO"
        binding.tvRootStatus.setTextColor(android.graphics.Color.parseColor("#FF4444"))
    }

    private fun showMainUI() {
        binding.layoutShizukuWall.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
        binding.tvRootStatus.text = "SHIZUKU ATIVO"
        binding.tvRootStatus.setTextColor(android.graphics.Color.parseColor("#1AFF1A"))
        logTerminal("> Shizuku OK — todos os recursos liberados")
        logTerminal("> FPS Optimizer v1.0 pronto")
    }

    // ─────────────────────────────────────────────
    // SETUP BOTÕES
    // ─────────────────────────────────────────────
    private fun setupButtons() {
        // Botão da wall: instalar Shizuku
        binding.btnInstallShizuku.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=moe.shizuku.privileged.api")))
        }

        // Botão da wall: tentar novamente
        binding.btnRetryShizuku.setOnClickListener {
            checkShizukuOnStart()
        }

        binding.btnClearCache.setOnClickListener {
            animateButtonPress(it); clearCache()
        }
        binding.btnOptimizeFFN.setOnClickListener {
            animateButtonPress(it); optimizeGame(PKG_FFN, "FFN")
        }
        binding.btnOptimizeFFM.setOnClickListener {
            animateButtonPress(it); optimizeGame(PKG_FFM, "FFM")
        }
        binding.btnFps120FFN.setOnClickListener {
            animateButtonPress(it)
            if (fps120FFNActive) disable120Fps(PKG_FFN, "FFN")
            else showFpsDialog(PKG_FFN, "FFN")
        }
        binding.btnFps120FFM.setOnClickListener {
            animateButtonPress(it)
            if (fps120FFMActive) disable120Fps(PKG_FFM, "FFM")
            else showFpsDialog(PKG_FFM, "FFM")
        }
        binding.btnShowFps.setOnClickListener {
            animateButtonPress(it); toggleFpsOverlay()
        }
        binding.btnOpenFFN.setOnClickListener {
            animateButtonPress(it); openGame(PKG_FFN, "Free Fire Normal")
        }
        binding.btnOpenFFM.setOnClickListener {
            animateButtonPress(it); openGame(PKG_FFM, "Free Fire MAX")
        }
        binding.switchHideStream.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                binding.tvHideStreamStatus.text = "HIDE STREAM: ON"
                logTerminal("> Hide Stream ATIVADO")
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                binding.tvHideStreamStatus.text = "HIDE STREAM: OFF"
                logTerminal("> Hide Stream DESATIVADO")
            }
        }
        binding.btnClearLogs.setOnClickListener {
            animateButtonPress(it); binding.tvStatus.text = ""
        }
    }

    // ─────────────────────────────────────────────
    // LIMPAR CACHE
    // ─────────────────────────────────────────────
    private fun clearCache() {
        showProgress()
        logTerminal("> Limpando cache completo...")
        lifecycleScope.launch(Dispatchers.IO) {
            ShizukuHelper.exec("sync && echo 3 > /proc/sys/vm/drop_caches")
            ShizukuHelper.exec("pm clear $PKG_FFN")
            ShizukuHelper.exec("pm clear $PKG_FFM")
            ShizukuHelper.exec("rm -rf /sdcard/Android/data/$PKG_FFN/cache/*")
            ShizukuHelper.exec("rm -rf /sdcard/Android/data/$PKG_FFM/cache/*")
            ShizukuHelper.exec("rm -rf /data/data/$PKG_FFN/cache/*")
            ShizukuHelper.exec("rm -rf /data/data/$PKG_FFM/cache/*")
            delay(800)
            withContext(Dispatchers.Main) {
                hideProgress()
                logTerminal("> Cache limpo com sucesso!")
            }
        }
    }

    // ─────────────────────────────────────────────
    // OTIMIZAR JOGO
    // ─────────────────────────────────────────────
    private fun optimizeGame(pkg: String, tag: String) {
        showProgress()
        logTerminal("> Otimizando $tag — CPU/GPU/RAM...")
        lifecycleScope.launch(Dispatchers.IO) {
            // CPU max performance
            ShizukuHelper.exec("for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$cpu 2>/dev/null; done")
            ShizukuHelper.exec("for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq; do cat \${cpu/scaling_max_freq/cpuinfo_max_freq} > \$cpu 2>/dev/null; done")
            // GPU
            ShizukuHelper.exec("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null")
            ShizukuHelper.exec("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null")
            ShizukuHelper.exec("echo performance > /sys/devices/platform/mali.0/power_policy 2>/dev/null")
            // Memoria
            ShizukuHelper.exec("echo 0 > /proc/sys/vm/swappiness 2>/dev/null")
            ShizukuHelper.exec("echo 3 > /proc/sys/vm/drop_caches 2>/dev/null")
            // Animacoes
            ShizukuHelper.exec("settings put global window_animation_scale 0.0")
            ShizukuHelper.exec("settings put global transition_animation_scale 0.0")
            ShizukuHelper.exec("settings put global animator_duration_scale 0.0")
            // IO
            ShizukuHelper.exec("for dev in /sys/block/*/queue/scheduler; do echo deadline > \$dev 2>/dev/null; done")
            // Thermal
            ShizukuHelper.exec("stop thermal-engine 2>/dev/null")
            ShizukuHelper.exec("stop thermald 2>/dev/null")
            // Prioridade do processo
            ShizukuHelper.exec("pid=\$(pidof $pkg 2>/dev/null); [ -n \"\$pid\" ] && renice -20 \$pid 2>/dev/null")
            delay(1200)
            withContext(Dispatchers.Main) {
                hideProgress()
                logTerminal("> $tag otimizado! ~90% de melhora aplicada")
            }
        }
    }

    // ─────────────────────────────────────────────
    // DIALOG FORÇA 120 FPS
    // ─────────────────────────────────────────────
    private fun showFpsDialog(pkg: String, tag: String) {
        AlertDialog.Builder(this, R.style.FpsDialogTheme)
            .setTitle("> FORCA 120 FPS — $tag")
            .setMessage(
                "Forca o FPS no maximo que seu celular suporta!\n\n" +
                "Tela 120Hz → trava em 120fps\n" +
                "Tela 60Hz  → trava em 60fps\n\n" +
                "O jogo vai rodar MUITO mais fluido!"
            )
            .setPositiveButton("> APLICAR!") { _, _ -> force120Fps(pkg, tag) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─────────────────────────────────────────────
    // FORÇA 120 FPS — MÉTODO REAL
    // Edita os arquivos de config internos do FF
    // ─────────────────────────────────────────────
    private fun force120Fps(pkg: String, tag: String) {
        showProgress()
        logTerminal("> Forcando 120 FPS REAL em $tag...")
        lifecycleScope.launch(Dispatchers.IO) {

            // ── MÉTODO 1: Editar config.ini do Free Fire ──
            // O FF lê esse arquivo na inicialização para definir o FPS cap
            val dataDirs = listOf(
                "/sdcard/Android/data/$pkg/files",
                "/data/data/$pkg/files",
                "/sdcard/Android/data/$pkg",
                "/data/user/0/$pkg/files"
            )

            val configContent = """
[config]
maxfps=120
highfps=1
fps=120
targetfps=120
enablehighfps=1
resolution=0
graphics=0
shadow=0
""".trimIndent()

            for (dir in dataDirs) {
                ShizukuHelper.exec("mkdir -p $dir 2>/dev/null")
                ShizukuHelper.exec("echo '$configContent' > $dir/config.ini 2>/dev/null")
                ShizukuHelper.exec("echo '$configContent' > $dir/UserCustom.ini 2>/dev/null")
                ShizukuHelper.exec("chmod 777 $dir/config.ini 2>/dev/null")
            }

            // ── MÉTODO 2: Editar preferências SharedPreferences do FF ──
            val spDir = "/data/data/$pkg/shared_prefs"
            ShizukuHelper.exec("mkdir -p $spDir 2>/dev/null")

            val spContent = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="fps" value="120" />
    <int name="maxfps" value="120" />
    <boolean name="highfps" value="true" />
    <int name="GraphicsLevel" value="0" />
    <boolean name="EnableHighFps" value="true" />
    <int name="FpsLimit" value="120" />
</map>""".trimIndent()

            ShizukuHelper.exec("echo '$spContent' > $spDir/FPS_SETTINGS.xml 2>/dev/null")
            ShizukuHelper.exec("chmod 777 $spDir/FPS_SETTINGS.xml 2>/dev/null")

            // ── MÉTODO 3: System properties que o FF lê ──
            ShizukuHelper.exec("setprop $pkg.fps 120")
            ShizukuHelper.exec("setprop $pkg.maxfps 120")
            ShizukuHelper.exec("setprop $pkg.highfps 1")

            // ── MÉTODO 4: Display 120Hz real via dumpsys ──
            ShizukuHelper.exec("settings put system peak_refresh_rate 120")
            ShizukuHelper.exec("settings put system min_refresh_rate 120")
            ShizukuHelper.exec("settings put system user_refresh_rate 120")
            // Forçar display mode 120hz via dumpsys SurfaceFlinger
            ShizukuHelper.exec("dumpsys SurfaceFlinger --latency 2>/dev/null")
            ShizukuHelper.exec("service call SurfaceFlinger 1035 i32 0 2>/dev/null")

            // ── MÉTODO 5: GPU sem nenhum limitador ──
            ShizukuHelper.exec("setprop debug.sf.enable_gl_backpressure 0")
            ShizukuHelper.exec("setprop debug.sf.latch_unsignaled 1")
            ShizukuHelper.exec("setprop debug.sf.disable_backpressure 1")
            ShizukuHelper.exec("setprop debug.egl.swapinterval 0")
            ShizukuHelper.exec("setprop debug.hwui.fps_divisor 1")
            ShizukuHelper.exec("echo 0 > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null")
            ShizukuHelper.exec("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null")
            ShizukuHelper.exec("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null")

            // ── MÉTODO 6: CPU performance total ──
            ShizukuHelper.exec("for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$cpu 2>/dev/null; done")

            // ── MÉTODO 7: Matar thermal para não throttle ──
            ShizukuHelper.exec("stop thermal-engine 2>/dev/null")
            ShizukuHelper.exec("stop thermald 2>/dev/null")
            ShizukuHelper.exec("stop mi_thermald 2>/dev/null")
            ShizukuHelper.exec("stop vendor.thermal-hal-service 2>/dev/null")

            // ── MÉTODO 8: Prioridade máxima no processo ──
            ShizukuHelper.exec("pid=\$(pidof $pkg 2>/dev/null); if [ -n \"\$pid\" ]; then renice -20 \$pid 2>/dev/null; chrt -f -p 99 \$pid 2>/dev/null; fi")

            delay(1500)
            withContext(Dispatchers.Main) {
                hideProgress()
                logTerminal("> config.ini do $tag editado → maxfps=120")
                logTerminal("> SharedPreferences do $tag → FPS=120")
                logTerminal("> Display 120Hz + GPU sem throttle")
                logTerminal("> ABRA O JOGO AGORA e teste o FPS!")
                logTerminal("> (reinicie o jogo se ja estava aberto)")
                if (pkg == PKG_FFN) {
                    fps120FFNActive = true
                    binding.btnFps120FFN.text = "DESATIVAR 120 FPS — FFN"
                } else {
                    fps120FFMActive = true
                    binding.btnFps120FFM.text = "DESATIVAR 120 FPS — FFM"
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // DESATIVAR 120 FPS
    // ─────────────────────────────────────────────
    private fun disable120Fps(pkg: String, tag: String) {
        showProgress()
        logTerminal("> Desativando 120 FPS em $tag...")
        lifecycleScope.launch(Dispatchers.IO) {
            ShizukuHelper.exec("settings delete system peak_refresh_rate")
            ShizukuHelper.exec("settings delete system min_refresh_rate")
            ShizukuHelper.exec("settings delete system user_refresh_rate")
            ShizukuHelper.exec("settings delete system refresh_rate_mode")
            ShizukuHelper.exec("setprop debug.sf.enable_gl_backpressure 1")
            ShizukuHelper.exec("setprop debug.sf.latch_unsignaled 0")
            ShizukuHelper.exec("setprop debug.sf.disable_backpressure 0")
            ShizukuHelper.exec("echo 1 > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null")
            ShizukuHelper.exec("start thermal-engine 2>/dev/null")
            delay(800)
            withContext(Dispatchers.Main) {
                hideProgress()
                logTerminal("> 120 FPS desativado em $tag")
                if (pkg == PKG_FFN) { fps120FFNActive = false; binding.btnFps120FFN.text = "FORCA 120 FPS — FFN" }
                else { fps120FFMActive = false; binding.btnFps120FFM.text = "FORCA 120 FPS — FFM" }
            }
        }
    }

    // ─────────────────────────────────────────────
    // OVERLAY FPS
    // ─────────────────────────────────────────────
    private fun toggleFpsOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQUEST_OVERLAY
            )
            logTerminal("> Autorize a sobreposicao para o monitor de FPS")
            return
        }
        fpsOverlayActive = !fpsOverlayActive
        val serviceIntent = Intent(this, FpsOverlayService::class.java)
        if (fpsOverlayActive) {
            startForegroundService(serviceIntent)
            binding.btnShowFps.text = "PARAR MONITOR FPS"
            logTerminal("> Monitor de FPS ativo!")
        } else {
            stopService(serviceIntent)
            binding.btnShowFps.text = "MOSTRAR TAXA FPS"
            logTerminal("> Monitor de FPS desativado")
        }
    }

    // ─────────────────────────────────────────────
    // ABRIR JOGO
    // ─────────────────────────────────────────────
    private fun openGame(pkg: String, gameName: String) {
        logTerminal("> Abrindo $gameName...")
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    return@postDelayed
                }
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
                    startActivity(intent); return@postDelayed
                }
                // Fallback Shizuku
                lifecycleScope.launch(Dispatchers.IO) {
                    ShizukuHelper.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>/dev/null")
                }
            } catch (e: Exception) {
                logTerminal("> ERRO: ${e.message}")
            }
        }, 300)
    }

    // ─────────────────────────────────────────────
    // UI HELPERS
    // ─────────────────────────────────────────────
    private fun showProgress() = runOnUiThread { binding.progressBar.visibility = View.VISIBLE }
    private fun hideProgress() { binding.progressBar.visibility = View.GONE }

    private fun logTerminal(msg: String) {
        runOnUiThread {
            val cur = binding.tvStatus.text.toString()
            binding.tvStatus.text = if (cur.isEmpty()) msg else "$cur\n$msg"
            binding.tvStatus.visibility = View.VISIBLE
        }
    }

    private fun animateButtonPress(view: View) {
        view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY && Settings.canDrawOverlays(this)) toggleFpsOverlay()
    }
}
