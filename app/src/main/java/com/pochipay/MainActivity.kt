package com.pochipay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.pochipay.databinding.ActivityMainBinding
import com.pochipay.viewmodels.PermissionViewModel
import com.pochipay.viewmodels.StartupState
import com.pochipay.viewmodels.StartupViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val permissionViewModel: PermissionViewModel by viewModels()
    private val startupViewModel: StartupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Observe the startup state to navigate when complete
        observeStartupState()

        // The permission check will run after the startup is complete
        permissionViewModel.hasAllPermissions.observe(this) { hasAllPermissions ->
            if (!hasAllPermissions) {
                // Ensure we don't navigate away from the startup flow
                if (navController.currentDestination?.id != R.id.registeringFragment) {
                    Timber.d("Permissions not granted, navigating to explanation screen")
                    navController.navigate(R.id.permissionExplanationFragment)
                }
            }
        }

        // Start the checks
        startupViewModel.beginStartupChecks()
        com.pochipay.update.GitHubUpdateChecker.schedulePeriodicChecks(applicationContext)

        // Handle bubble intent if launched from bubble
        handleBubbleIntent(intent)

        startPaymentVerificationWorker()
        
        // Start Foreground Service
        val serviceIntent = Intent(this, com.pochipay.services.PaymentMonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Manual Connect
        findViewById<android.view.View>(R.id.btn_manual_connect)?.setOnClickListener {
            Toast.makeText(this, "Connecting to server...", Toast.LENGTH_SHORT).show()
            
            // Force Check
            checkServerConnection()
            
            // Force Worker
            try {
                 val request = androidx.work.OneTimeWorkRequest.Builder(com.pochipay.workers.VerificationWorker::class.java)
                    .addTag("MANUAL_FORCE_POLL")
                    .build()
                 androidx.work.WorkManager.getInstance(this).enqueue(request)
            } catch (e: Exception) {
                Timber.e(e, "Manual poll failed")
            }
        }
    }

    private fun startPaymentVerificationWorker() {
        // Schedule periodic worker every 15 minutes
        val workRequest = androidx.work.PeriodicWorkRequest.Builder(
            com.pochipay.workers.VerificationWorker::class.java,
            15, java.util.concurrent.TimeUnit.MINUTES
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PaymentVerificationWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleBubbleIntent(intent)
    }

    /** Handles intent from bubble tap to navigate to conversation */
    private fun handleBubbleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                if (uri.scheme == "ntfy" && uri.host == "conversation") {
                    val topic = uri.lastPathSegment
                    Timber.d("Bubble tapped for conversation: $topic")
                    // TODO: Navigate to conversation detail when ready
                    // For now, just log it - navigation can be added when conversation detail is
                    // needed
                }
            }
        }
    }

    private fun observeStartupState() {
        lifecycleScope.launch {
            startupViewModel.startupState.collect { state ->
                val navHostFragment =
                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as
                                NavHostFragment
                val navController = navHostFragment.navController

                when (state) {
                    is StartupState.UpdateAvailable -> {
                        if (navController.currentDestination?.id == R.id.registeringFragment) {
                            navController.navigate(
                                    R.id.action_registeringFragment_to_updatingFragment
                            )
                        }
                    }
                    is StartupState.StartupComplete -> {
                        when (navController.currentDestination?.id) {
                            R.id.registeringFragment -> {
                                navController.navigate(
                                        R.id.action_registeringFragment_to_homeFragment
                                )
                            }
                            R.id.authorizationFragment -> {
                                navController.navigate(
                                        R.id.action_authorizationFragment_to_homeFragment
                                )
                            }
                        }
                    }
                    is StartupState.AuthorizationNeeded, is StartupState.AuthError -> {
                        if (navController.currentDestination?.id == R.id.registeringFragment) {
                            navController.navigate(
                                    R.id.action_registeringFragment_to_authorizationFragment
                            )
                        }
                    }
                    // For other states like Loading, RegistrationNeeded, ServerError, the
                    // respective fragments
                    // (RegisteringFragment or AuthorizationFragment) will handle their UI directly.
                    else -> {}
                }
            }
        }
    }

    private val statusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                "com.pochipay.ACTION_PROCESSING_TASK" -> {
                    findViewById<android.widget.ProgressBar>(R.id.indicator_processing)?.visibility = android.view.View.VISIBLE
                }
                "com.pochipay.ACTION_TASK_COMPLETED" -> {
                     findViewById<android.widget.ProgressBar>(R.id.indicator_processing)?.visibility = android.view.View.GONE
                     val successIcon = findViewById<android.widget.ImageView>(R.id.indicator_success)
                     successIcon?.visibility = android.view.View.VISIBLE
                     
                     // Fade out after 3 seconds
                     lifecycleScope.launch {
                         kotlinx.coroutines.delay(3000)
                         successIcon?.visibility = android.view.View.GONE
                     }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        val filter = android.content.IntentFilter().apply {
            addAction("com.pochipay.ACTION_PROCESSING_TASK")
            addAction("com.pochipay.ACTION_TASK_COMPLETED")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }

        // Only check permissions if startup is complete, otherwise it can interfere
        if (startupViewModel.startupState.value is StartupState.StartupComplete) {
            checkPermissions()
            startFastPolling() 
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }

    private fun startFastPolling() {
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500) // Initial delay
            while (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                // 1. Check Connection (Updates Green/Red Dot)
                checkServerConnection()
                
                // 2. Run Worker
                try {
                     val request = androidx.work.OneTimeWorkRequest.Builder(com.pochipay.workers.VerificationWorker::class.java)
                        .addTag("MANUAL_FAST_POLL")
                        .build()
                     androidx.work.WorkManager.getInstance(this@MainActivity).enqueue(request)
                } catch (e: Exception) {
                    Timber.e(e, "Fast polling failed")
                }
                
                kotlinx.coroutines.delay(2000) // Poll every 2 seconds
            }
        }
    }

    private fun checkServerConnection() {
        lifecycleScope.launch {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
            val customUrl = prefs.getString("custom_server_url", "")?.trim()
            val indicator = findViewById<android.view.View>(R.id.indicator_connection)
            
            if (!customUrl.isNullOrEmpty()) {
                val isConnected = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val url = java.net.URL("$customUrl/api/payment-status")
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 1500
                        connection.readTimeout = 1500
                        connection.requestMethod = "GET"
                        connection.responseCode == 200
                    } catch (e: Exception) {
                        false
                    }
                }

                if (isConnected) {
                    indicator?.setBackgroundResource(R.drawable.indicator_dot_green)
                } else {
                    indicator?.setBackgroundResource(R.drawable.indicator_dot_red)
                }
            } else {
                indicator?.setBackgroundResource(R.drawable.indicator_dot_red)
            }
        }
    }

    private fun checkPermissions() {
        val hasAccessibility = isAccessibilityServiceEnabled()
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasStandard = hasStandardPermissions()

        val hasAll = hasAccessibility && hasOverlay && hasStandard
        permissionViewModel.setHasAllPermissions(hasAll)
        Timber.d(
                "Permission status: accessibility=$hasAccessibility, overlay=$hasOverlay, standard=$hasStandard, all=$hasAll"
        )
    }

    private fun hasStandardPermissions(): Boolean {
        val permissions =
                mutableListOf(
                                android.Manifest.permission.RECEIVE_SMS,
                                android.Manifest.permission.READ_SMS,
                                android.Manifest.permission.SEND_SMS,
                                android.Manifest.permission.READ_CONTACTS
                        )
                        .apply {
                            if (android.os.Build.VERSION.SDK_INT >=
                                            android.os.Build.VERSION_CODES.TIRAMISU
                            ) {
                                add(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

        return permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + MyAccessibilityService::class.java.canonicalName
        val enabledServices =
                Settings.Secure.getString(
                        contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
        return enabledServices?.contains(service, ignoreCase = true) == true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_stk_console -> {
                val navController = findNavController(R.id.nav_host_fragment)
                navController.navigate(R.id.automationFragment)
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
