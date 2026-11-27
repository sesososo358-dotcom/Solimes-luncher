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
    private val io: ExecutorService =
