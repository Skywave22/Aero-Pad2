package com.aeropad.remote.viewmodel

import androidx.lifecycle.ViewModel
import com.aeropad.remote.data.text.TextToolsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin Hilt seam so the Text & Clips screen can observe [TextToolsStore]. */
@HiltViewModel
class TextToolsViewModel @Inject constructor(
    val store: TextToolsStore
) : ViewModel()
