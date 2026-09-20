package com.pochipay.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PermissionViewModel : ViewModel() {

    private val _hasAllPermissions = MutableLiveData<Boolean>()
    val hasAllPermissions: LiveData<Boolean> = _hasAllPermissions

    fun setHasAllPermissions(hasAllPermissions: Boolean) {
        _hasAllPermissions.value = hasAllPermissions
    }
}
