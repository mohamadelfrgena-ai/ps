package com.addonstation.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.decode.GifDecoder
import com.addonstation.app.core.AssetsRepository
import com.addonstation.app.core.AuthRepository
import com.addonstation.app.core.ChatRepository
import com.addonstation.app.core.ContentRepository
import com.addonstation.app.core.CreatorsRepository
import com.addonstation.app.core.PaymentsRepository
import com.addonstation.app.core.ProfileRepository
import com.addonstation.app.core.RealtimeClient
import com.addonstation.app.core.SessionManager
import com.addonstation.app.core.SupabaseBackend
import com.addonstation.app.core.WalletRepository

data class PendingRecovery(val accessToken: String, val refreshToken: String)

/** Manual DI container (no reflection, no codegen — always compiles). */
class AppContainer(val appContext: Context) {
    val session = SessionManager(appContext)

    lateinit var backend: SupabaseBackend
        private set
    lateinit var realtime: RealtimeClient
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var profiles: ProfileRepository
        private set
    lateinit var content: ContentRepository
        private set
    lateinit var chat: ChatRepository
        private set
    lateinit var wallet: WalletRepository
        private set
    lateinit var payments: PaymentsRepository
        private set
    lateinit var creators: CreatorsRepository
        private set
    lateinit var assets: AssetsRepository
        private set

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .components { add(GifDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

    @Volatile var ready: Boolean = false
        private set

    var pendingRecovery: PendingRecovery? = null

    /** Builds backend + repositories from the saved connection, then restores the session. */
    suspend fun init() {
        if (ready) return
        backend = SupabaseBackend(session.backendConfig())
        realtime = RealtimeClient(backend)
        auth = AuthRepository(backend, session)
        profiles = ProfileRepository(backend)
        content = ContentRepository(backend)
        chat = ChatRepository(backend)
        wallet = WalletRepository(backend)
        payments = PaymentsRepository(backend)
        creators = CreatorsRepository(backend)
        assets = AssetsRepository(backend)
        auth.restore()
        ready = true
    }
}

class AddonStationApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
