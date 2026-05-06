package com.launcher.nova.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.nova.model.AppInfo
import com.launcher.nova.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _apps.value = repository.getInstalledApps()
            _isLoading.value = false
        }
    }

    fun getLaunchIntent(packageName: String) = repository.getLaunchIntent(packageName)
}
