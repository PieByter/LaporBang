package com.xeraphion.laporbang.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@Suppress("UNCHECKED_CAST")
class EditAccountViewModelFactory(private val token: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditAccountViewModel::class.java)) {
            return EditAccountViewModel(token) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
