package com.drclicker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.drclicker.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var pendingEngineStart = false

    // SharedPreferences listener — receives log lines written by AutoAcceptEngineService
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "last_log") {
            val msg = prefs.getString("last_log", null) ?: return@OnSharedPreferenceChangeListener
            appendLog(msg)
            refreshStats()
        }
    }

    // Launcher for picking a template image from gallery
    private val imagePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri -> handleTemplateUri(uri) }
            }
        }

    // Launcher for overlay permission result
    private val overlayPermLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (pendingEngineStart) {
                pendingEngineStart = false
                // Re-run the activation checks now that the user returned
                attemptEngineActivation()
            }
        }

    // Launcher for media permission (READ_MEDIA_IMAGES)
    private val mediaPermLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openImagePicker()
            } else {
                Toast.makeText(this, "Storage permission required to load a template image.", Toast.LENGTH_LONG).show()
            }
        }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        restoreUiFromPrefs()
        setupListeners()
        refreshStats()
        appendLog("→ Dr. Clicker ready. Configure filters and load a template.")
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onStop() {
        super.onStop()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onResume() {
        super.onResume()
        // Sync switch state with actual service state on resume
        val engineActive = AutoAcceptEngineService.instance != null
        binding.switchEngine.isChecked = engineActive
        updateEngineUi(engineActive)
        refreshStats()
    }

    // ─── UI Setup ─────────────────────────────────────────────────────────────

    private fun restoreUiFromPrefs() {
        binding.etMinPrice.setText(prefs.getString(KEY_MIN_PRICE, ""))
        binding.etMaxPrice.setText(prefs.getString(KEY_MAX_PRICE, ""))
        binding.etMinPickup.setText(prefs.getString(KEY_MIN_PICKUP, ""))
        binding.etMaxDrop.setText(prefs.getString(KEY_MAX_DROP, ""))
        binding.seekbarThreshold.progress = prefs.getInt(KEY_THRESHOLD, 85)
        binding.tvThresholdValue.text = "${binding.seekbarThreshold.progress}%"

        // Restore template thumbnail if one was saved
        val templatePath = prefs.getString(KEY_TEMPLATE_PATH, null)
        if (templatePath != null && File(templatePath).exists()) {
            val bmp = BitmapFactory.decodeFile(templatePath)
            if (bmp != null) {
                showTemplateThumbnail(bmp, templatePath)
            }
        }
    }

    private fun setupListeners() {
        // ── Text watchers: persist filter values on change ──
        binding.etMinPrice.addChangedListener { prefs.edit().putString(KEY_MIN_PRICE, it).apply() }
        binding.etMaxPrice.addChangedListener { prefs.edit().putString(KEY_MAX_PRICE, it).apply() }
        binding.etMinPickup.addChangedListener { prefs.edit().putString(KEY_MIN_PICKUP, it).apply() }
        binding.etMaxDrop.addChangedListener { prefs.edit().putString(KEY_MAX_DROP, it).apply() }

        // ── Threshold seek bar ──
        binding.seekbarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvThresholdValue.text = "$progress%"
                prefs.edit().putInt(KEY_THRESHOLD, progress).apply()
                AutoAcceptEngineService.instance?.updateThreshold(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // ── Template picker ──
        binding.templatePickerWrapper.setOnClickListener {
            checkMediaPermissionAndPick()
        }

        binding.btnClearTemplate.setOnClickListener {
            clearTemplate()
        }

        // ── Engine switch ──
        binding.switchEngine.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                attemptEngineActivation()
            } else {
                deactivateEngine()
            }
        }

        // ── Log clear ──
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
            appendLog("→ Log cleared.")
        }
    }

    // ─── Permissions & Engine Activation (Checks A → B → C) ──────────────────

    /**
     * Check A: Accessibility enabled?
     * Check B: Overlay permission granted?
     * Check C: Activate engine.
     */
    private fun attemptEngineActivation() {
        // Check A — Accessibility
        if (!isAccessibilityEnabled()) {
            binding.switchEngine.isChecked = false
            Toast.makeText(
                this,
                "Please enable Dr. Clicker in Accessibility Settings",
                Toast.LENGTH_LONG
            ).show()
            appendLog("⚠ Accessibility not enabled. Redirecting to settings…")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        // Check B — Overlay permission
        if (!Settings.canDrawOverlays(this)) {
            pendingEngineStart = true
            appendLog("⚠ Overlay permission required. Requesting…")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermLauncher.launch(intent)
            return
        }

        // Check C — Template loaded?
        val templatePath = prefs.getString(KEY_TEMPLATE_PATH, null)
        if (templatePath == null || !File(templatePath).exists()) {
            binding.switchEngine.isChecked = false
            Toast.makeText(
                this,
                "Please load an Accept button template image first.",
                Toast.LENGTH_LONG
            ).show()
            appendLog("⚠ No template loaded. Please upload the Accept button crop.")
            return
        }

        activateEngine(templatePath)
    }

    private fun activateEngine(templatePath: String) {
        saveFiltersToPrefs()

        // Push filter config into the live service (if already running from a prior session)
        AutoAcceptEngineService.instance?.reloadConfig()

        // Start floating overlay service
        val overlayIntent = Intent(this, FloatingOverlayService::class.java)
        ContextCompat.startForegroundService(this, overlayIntent)

        updateEngineUi(true)
        appendLog("✔ Engine ACTIVATED — watching for Rapido requests…")
    }

    private fun deactivateEngine() {
        stopService(Intent(this, FloatingOverlayService::class.java))
        AutoAcceptEngineService.instance?.setEngineActive(false)
        updateEngineUi(false)
        appendLog("■ Engine STOPPED.")
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedComponent = "$packageName/.AutoAcceptEngineService"
        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }

    // ─── Template Handling ────────────────────────────────────────────────────

    private fun checkMediaPermissionAndPick() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val perm = android.Manifest.permission.READ_MEDIA_IMAGES
            if (checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openImagePicker()
            } else {
                mediaPermLauncher.launch(perm)
            }
        } else {
            val perm = android.Manifest.permission.READ_EXTERNAL_STORAGE
            if (checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openImagePicker()
            } else {
                mediaPermLauncher.launch(perm)
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        imagePickerLauncher.launch(intent)
    }

    private fun handleTemplateUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: run {
                Toast.makeText(this, "Cannot open selected image.", Toast.LENGTH_SHORT).show()
                return
            }
            val bmp = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bmp == null) {
                Toast.makeText(this, "Failed to decode image.", Toast.LENGTH_SHORT).show()
                return
            }

            // Persist the bitmap to internal storage for cross-process access
            val file = File(filesDir, TEMPLATE_FILENAME)
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            prefs.edit().putString(KEY_TEMPLATE_PATH, file.absolutePath).apply()
            showTemplateThumbnail(bmp, file.absolutePath)

            // Push template into running service immediately
            AutoAcceptEngineService.instance?.loadTemplate(file.absolutePath)

            appendLog("✔ Template loaded — ${bmp.width}×${bmp.height}px")

        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_LONG).show()
            appendLog("✘ Template load error: ${e.message}")
        }
    }

    private fun showTemplateThumbnail(bmp: Bitmap, path: String) {
        binding.ivTemplatePreview.setImageBitmap(bmp)
        binding.ivTemplatePreview.visibility = android.view.View.VISIBLE
        binding.emptyTemplateHint.visibility = android.view.View.GONE
        binding.btnClearTemplate.visibility = android.view.View.VISIBLE
        binding.tvTemplateStatus.text = "Template loaded ✔"
        binding.tvTemplateSize.text = "${bmp.width}×${bmp.height}px"
    }

    private fun clearTemplate() {
        prefs.edit().remove(KEY_TEMPLATE_PATH).apply()
        val file = File(filesDir, TEMPLATE_FILENAME)
        if (file.exists()) file.delete()
        AutoAcceptEngineService.instance?.clearTemplate()

        binding.ivTemplatePreview.visibility = android.view.View.GONE
        binding.ivTemplatePreview.setImageDrawable(null)
        binding.emptyTemplateHint.visibility = android.view.View.VISIBLE
        binding.btnClearTemplate.visibility = android.view.View.GONE
        binding.tvTemplateStatus.text = "No template loaded"
        binding.tvTemplateSize.text = ""
        appendLog("✘ Template cleared.")
    }

    // ─── UI State Helpers ─────────────────────────────────────────────────────

    private fun updateEngineUi(active: Boolean) {
        if (active) {
            binding.tvStatus.text = "ACTIVE"
            binding.tvStatus.setTextColor(getColor(R.color.status_active))
            binding.statusDot.setBackgroundResource(R.drawable.bg_status_dot_active)
            binding.tvEngineSubtitle.text = "Engine is running — monitoring Rapido Captain"
        } else {
            binding.tvStatus.text = "IDLE"
            binding.tvStatus.setTextColor(getColor(R.color.status_inactive))
            binding.statusDot.setBackgroundResource(R.drawable.bg_status_dot_inactive)
            binding.tvEngineSubtitle.text = "Tap to start auto-accepting rides"
        }
    }

    fun refreshStats() {
        binding.tvAcceptedCount.text = prefs.getInt(KEY_STAT_ACCEPTED, 0).toString()
        binding.tvSkippedCount.text = prefs.getInt(KEY_STAT_SKIPPED, 0).toString()
        binding.tvClickCount.text = prefs.getInt(KEY_STAT_CLICKS, 0).toString()
    }

    fun appendLog(message: String) {
        runOnUiThread {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            binding.tvLog.append("[$ts] $message\n")
            binding.scrollLog.post { binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }

    // ─── Prefs Helpers ────────────────────────────────────────────────────────

    private fun saveFiltersToPrefs() {
        prefs.edit()
            .putString(KEY_MIN_PRICE, binding.etMinPrice.text.toString())
            .putString(KEY_MAX_PRICE, binding.etMaxPrice.text.toString())
            .putString(KEY_MIN_PICKUP, binding.etMinPickup.text.toString())
            .putString(KEY_MAX_DROP, binding.etMaxDrop.text.toString())
            .putInt(KEY_THRESHOLD, binding.seekbarThreshold.progress)
            .apply()
    }

    // ─── Extension ────────────────────────────────────────────────────────────

    private fun android.widget.EditText.addChangedListener(onChanged: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onChanged(s.toString()) }
        })
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        const val PREFS_NAME = "dr_clicker_prefs"
        const val KEY_MIN_PRICE = "min_price"
        const val KEY_MAX_PRICE = "max_price"
        const val KEY_MIN_PICKUP = "min_pickup"
        const val KEY_MAX_DROP = "max_drop"
        const val KEY_THRESHOLD = "threshold"
        const val KEY_TEMPLATE_PATH = "template_path"
        const val KEY_STAT_ACCEPTED = "stat_accepted"
        const val KEY_STAT_SKIPPED = "stat_skipped"
        const val KEY_STAT_CLICKS = "stat_clicks"
        const val TEMPLATE_FILENAME = "accept_template.png"
    }
}
