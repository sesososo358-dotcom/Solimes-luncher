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
import com.solimes.launcher.core.GameRuntime
import com.solimes.launcher.core.InstallRequest
import com.solimes.launcher.core.InstanceInstaller
import com.solimes.launcher.core.InstanceMeta
import com.solimes.launcher.core.LaunchBuilder
import com.solimes.launcher.core.RuntimeProvider
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
        set
