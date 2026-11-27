package com.solimes.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.solimes.launcher.accounts.AccountManager
import com.solimes.launcher.core.AppLog
import com.solimes.launcher.core.GamePaths
import com.solimes.launcher.core.InstallRequest
import com.solimes.launcher.core.InstanceInstaller
import com.solimes.launcher.core.InstanceMeta
import com.solimes.launcher.core.LaunchBuilder
import com.solimes.launcher.core.RuntimeProvider
import com.solimes.launcher.core.UiTestRuntime
import com.solimes.launcher.core.Versions
import com.solimes.launcher.nativebridge.NativeBridge
import com.solimes.launcher.performance.PerformancePresetManager
import com.solimes.launcher.skins.SkinManager
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var paths: GamePaths
    private lateinit var prefs: SharedPreferences
    private lateinit var accounts: AccountManager
    private lateinit var skins: SkinManager
    private lateinit var content: FrameLayout
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var statusView: TextView? = null
    private var pendingStatus: String = "Ready."
    @Volatile private var busy = false

    private val green = Color.parseColor("#9BFF7A")
    private val bg = Color.parseColor("#0F1A10")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeBridge.ensureLoaded()
        paths = GamePaths(this)
        prefs = getSharedPreferences("solimes", MODE_PRIVATE)
        accounts = AccountManager(paths)
        skins = SkinManager(this, paths)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Play", "Accounts", "Performance", "Logs").forEachIndexed { i, title ->
            nav.addView(
                Button(this).apply { text = title; textSize = 12f; setOnClickListener { show(i) } },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        content = FrameLayout(this)
        root.addView(nav)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        show(0)
    }

    // ---------- helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(24)) }
    private fun label(s: String, size: Float = 14f, color: Int = Color.WHITE) =
        TextView(this).apply { text = s; textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(4)) }
    private fun button(s: String, onClick: () -> Unit) =
        Button(this).apply { text = s; setOnClickListener { onClick() } }
    private fun checkbox(s: String, key: String, def: Boolean) = CheckBox(this).apply {
        text = s; setTextColor(Color.WHITE); isChecked = prefs.getBoolean(key, def)
        setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(key, v).apply() }
    }
    private fun field(hint: String, value: String) = EditText(this).apply {
        this.hint = hint; setText(value); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); isSingleLine = true
    }

    private fun say(msg: String) {
        AppLog.add(0, msg)
        runOnUiThread { pendingStatus = msg; statusView?.text = msg }
    }

    private fun show(tab: Int) {
        content.removeAllViews()
        val page = when (tab) { 0 -> playPage(); 1 -> accountsPage(); 2 -> performancePage(); else -> logsPage() }
        content.addView(ScrollView(this).apply { addView(page) })
    }

    // ---------- Play ----------
    private fun playPage(): View {
        val col = column()
        col.addView(ImageView(this).apply { setImageResource(R.drawable.solimes_logo) },
            LinearLayout.LayoutParams(dp(72), dp(72)))
        col.addView(label("Solimes Launcher", 22f, green))
        col.addView(label("Minecraft version (all run on Fabric)"))

        val current = prefs.getString("version", Versions.SUPPORTED.first())
        val group = RadioGroup(this)
        Versions.SUPPORTED.forEach { v ->
            group.addView(RadioButton(this).apply {
                text = if (Versions.isLegacy(v)) "$v  (Legacy Fabric)" else v
                setTextColor(Color.WHITE)
                id = View.generateViewId()
                isChecked = v == current
                setOnClickListener { prefs.edit().putString("version", v).apply() }
            })
        }
        col.addView(group)

        col.addView(checkbox("Solimes Client (Fabric + Mod Menu) - downloads the client", "client", true))
        col.addView(checkbox("Allow Mod Menu", "allowModMenu", true))
        col.addView(checkbox("Apply low-FPS preset (Galaxy A04s)", "autoPerf", true))

        col.addView(label("Edition text for plain Fabric instances (window title)"))
        val edition = field("C++ Edition", prefs.getString("edition", "C++ Edition") ?: "C++ Edition")
        col.addView(edition)
        col.addView(label("Your GitHub repo that hosts the Solimes Client release (user/repo)"))
        val repo = field("user/repo", prefs.getString("releaseRepo", "") ?: "")
        col.addView(repo)

        col.addView(button("Download / Install") { install(repo.text.toString().trim(), edition.text.toString().trim()) })
        col.addView(button("Launch") { launch() })
        val status = label(pendingStatus, 13f, green)
        statusView = status
        col.addView(status)
        return col
    }

    private fun install(repo: String, edition: String) {
        if (busy) { say("Another task is running..."); return }
        busy = true
        prefs.edit().putString("releaseRepo", repo).putString("edition", edition).apply()
        val mc = prefs.getString("version", Versions.SUPPORTED.first())!!
        val client = prefs.getBoolean("client", true)
        val req = InstallRequest(
            mc = mc,
            client = client,
            allowModMenu = prefs.getBoolean("allowModMenu", true),
            windowTitle = if (client) "Solimes Client" else edition.ifEmpty { "C++ Edition" },
            releaseRepo = repo,
            applyPerformancePreset = prefs.getBoolean("autoPerf", true),
            skin = selectedSkin()
        )
        io.execute {
            try {
                InstanceInstaller(paths, skins) { say(it) }.install(req)
            } catch (t: Throwable) {
                AppLog.add(2, "Install failed: ${t.message}")
                say("ERROR: ${t.message}")
            } finally {
                busy = false
            }
        }
    }

    private fun launch() {
        if (busy) { say("Another task is running..."); return }
        busy = true
        val mc = prefs.getString("version", Versions.SUPPORTED.first())!!
        val client = prefs.getBoolean("client", true)
        io.execute {
            try {
                val dir = paths.instanceDir(mc, client)
                val meta = InstanceMeta.load(dir) ?: throw IllegalStateException("Not installed yet - press Download / Install first")
                val account = accounts.selected() ?: throw IllegalStateException("Create a profile on the Accounts tab first")
                val spec = LaunchBuilder.build(paths, meta, account, PerformancePresetManager.jvmArgs(mc))
                File(dir, "launch-command.txt").writeText(spec.commandLine().joinToString(" \\\n  "))
                RuntimeProvider.current = UiTestRuntime(this)
                RuntimeProvider.current.launch(spec).getOrThrow()
                say("Launched Minecraft $mc as${account.name}")
            
