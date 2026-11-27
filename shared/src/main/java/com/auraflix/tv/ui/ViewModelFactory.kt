package com.auraflix.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Factory tối giản cho viewModel không có dependency injection framework. */
fun <VM : ViewModel> factoryOf(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
