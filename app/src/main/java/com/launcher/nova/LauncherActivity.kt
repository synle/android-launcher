package com.launcher.nova

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.launcher.nova.adapter.AppAdapter
import com.launcher.nova.databinding.ActivityLauncherBinding
import com.launcher.nova.model.AppInfo
import com.launcher.nova.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private val viewModel: LauncherViewModel by viewModels()

    private val appAdapter = AppAdapter(
        onClick = ::onAppClicked,
        onLongClick = ::onAppLongClicked,
    )

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupRecyclerView()
        setupBackHandler()
        observeViewModel()
        registerPackageReceiver()

        viewModel.loadApps()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appGrid) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = insets.left + 8,
                top = insets.top + 8,
                right = insets.right + 8,
                bottom = insets.bottom + 8,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupRecyclerView() {
        binding.appGrid.apply {
            layoutManager = GridLayoutManager(this@LauncherActivity, GRID_COLUMNS)
            adapter = appAdapter
            setHasFixedSize(true)
            itemAnimator = null // Disable animations for snappy feel
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Launcher should not exit on back press — scroll to top
                binding.appGrid.scrollToPosition(0)
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.apps.collect { apps ->
                        appAdapter.submitList(apps)
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun onAppClicked(app: AppInfo) {
        val intent = viewModel.getLaunchIntent(app.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onAppLongClicked(app: AppInfo): Boolean {
        // Placeholder for future drag/drop, app info, or uninstall
        Toast.makeText(this, app.label, Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing HOME while already on home — scroll to top
        if (Intent.ACTION_MAIN == intent.action) {
            binding.appGrid.scrollToPosition(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
    }

    companion object {
        private const val GRID_COLUMNS = 4
    }
}
