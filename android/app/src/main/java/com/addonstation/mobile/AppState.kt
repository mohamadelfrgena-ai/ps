package com.addonstation.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.addonstation.mobile.data.Profile
import com.addonstation.mobile.data.Repository
import com.addonstation.mobile.data.Supabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppState(app: Application) : AndroidViewModel(app) {
    val sb = Supabase(app)
    val repo = Repository(sb)
    val profile = MutableStateFlow<Profile?>(null)
    val balance = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch { sb.session.collect { reload() } }
    }

    fun reload() = viewModelScope.launch {
        val s = sb.session.value
        if (s == null) { profile.value = null; balance.value = null; return@launch }
        runCatching { profile.value = repo.profile(s.userId) }
        runCatching { balance.value = repo.wallet(s.userId) ?: 0 }
    }
}
