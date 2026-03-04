package com.uninstallprevention.ui

import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uninstallprevention.R
import com.uninstallprevention.admin.DeviceAdminReceiver
import com.uninstallprevention.data.AppInfo
import com.uninstallprevention.data.PreferenceManager
import com.uninstallprevention.service.CountdownService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var searchView: EditText
    private lateinit var tvAdminStatus: TextView
    private lateinit var btnEnableAdmin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvAppCount: TextView

    private val ADMIN_REQUEST_CODE = 1001
    private val allApps = mutableListOf<AppInfo>()

    private val countdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.getStringExtra(CountdownService.EXTRA_PACKAGE_NAME) ?: return
            when (intent.action) {
                CountdownService.BROADCAST_TICK -> {
                    val remaining = intent.getLongExtra(CountdownService.EXTRA_REMAINING_MS, 0)
                    adapter.updateCountdown(packageName, remaining)
                }
                CountdownService.BROADCAST_DONE -> {
                    adapter.updateCountdown(packageName, -1)
                    adapter.updateProtection(packageName, false)
                    Toast.makeText(this@MainActivity,
                        "Protection removed — app can now be uninstalled",
                        Toast.LENGTH_LONG).show()
                }
                CountdownService.BROADCAST_CANCELLED -> {
                    adapter.updateCountdown(packageName, -1)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        bindViews()
        setupRecyclerView()
        setupSearch()
        updateAdminStatus()
        loadApps()
        registerBroadcastReceiver()
    }

    private fun bindViews() {
        recyclerView = findViewById(R.id.recyclerView)
        searchView = findViewById(R.id.etSearch)
        tvAdminStatus = findViewById(R.id.tvAdminStatus)
        btnEnableAdmin = findViewById(R.id.btnEnableAdmin)
        progressBar = findViewById(R.id.progressBar)
        tvAppCount = findViewById(R.id.tvAppCount)

        btnEnableAdmin.setOnClickListener { requestAdminPrivileges() }
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(
            onProtectToggled = { appInfo, shouldProtect ->
                handleProtectToggle(appInfo, shouldProtect)
            },
            onCancelCountdown = { appInfo ->
                handleCancelCountdown(appInfo)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isEmpty()) allApps.toList()
        else allApps.filter { it.appName.contains(query, ignoreCase = true) }
        adapter.submitList(filtered)
        tvAppCount.text = "${filtered.size} apps"
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        Thread {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
                    // Filter: user-installed apps + exclude ourselves
                    app.packageName != packageName &&
                    (app.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                     app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
                }
                .sortedBy { pm.getApplicationLabel(it).toString() }

            val apps = packages.map { appInfo ->
                val pkgName = appInfo.packageName
                AppInfo(
                    packageName = pkgName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    isProtected = PreferenceManager.isAppProtected(this, pkgName),
                    countdownRemainingMs = PreferenceManager.getRemainingMillis(this, pkgName)
                )
            }

            runOnUiThread {
                allApps.clear()
                allApps.addAll(apps)
                adapter.submitList(apps)
                tvAppCount.text = "${apps.size} apps"
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun handleProtectToggle(appInfo: AppInfo, shouldProtect: Boolean) {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            showAdminRequiredDialog()
            return
        }

        if (shouldProtect) {
            // Instant ON — no timer
            PreferenceManager.addProtectedApp(this, appInfo.packageName)
            adapter.updateProtection(appInfo.packageName, true)
            Toast.makeText(this, "✓ ${appInfo.appName} is now protected", Toast.LENGTH_SHORT).show()
        } else {
            // Check if countdown is already active
            if (PreferenceManager.hasActiveCountdown(this, appInfo.packageName)) {
                Toast.makeText(this, "Countdown already running for ${appInfo.appName}", Toast.LENGTH_SHORT).show()
                return
            }
            showStartCountdownDialog(appInfo)
        }
    }

    private fun showStartCountdownDialog(appInfo: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle("⏳ Start 24-Hour Countdown?")
            .setMessage("Protection for ${appInfo.appName} will be removed after 24 hours.\n\nYou can cancel anytime, but re-triggering will restart the full 24-hour timer.")
            .setPositiveButton("Start Countdown") { _, _ ->
                startCountdownForApp(appInfo)
            }
            .setNegativeButton("Keep Protected", null)
            .show()
    }

    private fun startCountdownForApp(appInfo: AppInfo) {
        val endTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24)
        PreferenceManager.setCountdownEndTime(this, appInfo.packageName, endTime)
        adapter.updateCountdown(appInfo.packageName, TimeUnit.HOURS.toMillis(24))

        val serviceIntent = Intent(this, CountdownService::class.java).apply {
            action = CountdownService.ACTION_START
            putExtra(CountdownService.EXTRA_PACKAGE_NAME, appInfo.packageName)
            putExtra(CountdownService.EXTRA_APP_NAME, appInfo.appName)
        }
        startForegroundService(serviceIntent)
        Toast.makeText(this, "⏳ 24-hour countdown started for ${appInfo.appName}", Toast.LENGTH_LONG).show()
    }

    private fun handleCancelCountdown(appInfo: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle("Cancel Countdown?")
            .setMessage("This will cancel the timer. ${appInfo.appName} will remain protected.\n\nNote: Starting a new countdown will reset the timer to 24 hours.")
            .setPositiveButton("Cancel Timer") { _, _ ->
                val serviceIntent = Intent(this, CountdownService::class.java).apply {
                    action = CountdownService.ACTION_CANCEL
                    putExtra(CountdownService.EXTRA_PACKAGE_NAME, appInfo.packageName)
                }
                startForegroundService(serviceIntent)
                adapter.updateCountdown(appInfo.packageName, -1)
                Toast.makeText(this, "Timer cancelled. ${appInfo.appName} stays protected.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Keep Timer Running", null)
            .show()
    }

    private fun updateAdminStatus() {
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            tvAdminStatus.text = "🛡️ Device Admin: Active"
            tvAdminStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnEnableAdmin.visibility = View.GONE
        } else {
            tvAdminStatus.text = "⚠️ Device Admin: Not Active — Protection disabled"
            tvAdminStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnEnableAdmin.visibility = View.VISIBLE
        }
    }

    private fun requestAdminPrivileges() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Uninstall Prevention needs Device Admin access to protect apps from being uninstalled.")
        }
        startActivityForResult(intent, ADMIN_REQUEST_CODE)
    }

    private fun showAdminRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Device Admin Required")
            .setMessage("You must enable Device Admin access before protecting apps.")
            .setPositiveButton("Enable Now") { _, _ -> requestAdminPrivileges() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(CountdownService.BROADCAST_TICK)
            addAction(CountdownService.BROADCAST_DONE)
            addAction(CountdownService.BROADCAST_CANCELLED)
        }
        registerReceiver(countdownReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADMIN_REQUEST_CODE) {
            updateAdminStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAdminStatus()
    }

    override fun onDestroy() {
        unregisterReceiver(countdownReceiver)
        super.onDestroy()
    }
}
