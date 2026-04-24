package com.example.enterprisedocumentredactor.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.enterprisedocumentredactor.data.local.SessionCache
import com.example.enterprisedocumentredactor.domain.model.RedactionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val sessionCache: SessionCache
) : ViewModel() {

    val result: StateFlow<RedactionResult?> = sessionCache.lastResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
