package com.addonstation.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SessionState(
    val loading: Boolean = true,
    val userId: String? = null,
    val profile: Profile? = null
)

/** Auth + session lifecycle + current profile, mirroring the web app's auth flow. */
class AuthRepository(
    val backend: SupabaseBackend,
    private val session: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile private var accessToken: String? = null

    init {
        backend.tokenProvider = { accessToken }
    }

    fun currentUserId(): String? = _state.value.userId
    fun currentProfile(): Profile? = _state.value.profile

    suspend fun restore() {
        _state.value = SessionState(loading = true)
        try {
            val storedAccess = session.accessToken.first()
            val storedRefresh = session.refreshToken.first()
            if (storedAccess.isNullOrBlank() || storedRefresh.isNullOrBlank()) {
                _state.value = SessionState(loading = false)
                return
            }
            accessToken = storedAccess
            if (!session.isSessionFresh()) {
                try {
                    val fresh = backend.authRefresh(storedRefresh)
                    accessToken = fresh.accessToken
                    session.saveSession(fresh)
                } catch (e: Exception) {
                    // Refresh failed: drop session (same as expired web session)
                    accessToken = null
                    session.clearSession()
                    _state.value = SessionState(loading = false)
                    return
                }
            }
            val uid = session.userId.first()
                ?: backend.authGetUser(accessToken!!).id.also { /* keep */ }
            val profile = loadProfile(uid)
            _state.value = SessionState(loading = false, userId = uid, profile = profile)
        } catch (e: Exception) {
            _state.value = SessionState(loading = false)
        }
    }

    suspend fun refreshProfile() {
        val uid = currentUserId() ?: return
        _state.value = _state.value.copy(profile = loadProfile(uid))
    }

    private suspend fun loadProfile(uid: String): Profile? = try {
        backend.one<Profile>("profiles", "*", mapOf("id" to "eq.$uid"))
    } catch (_: Exception) { null }

    /** Creates the profile row if the DB trigger did not (defensive, like the web fallback). */
    private suspend fun ensureProfile(uid: String, username: String, email: String) {
        try {
            val existing = backend.one<Profile>("profiles", "id", mapOf("id" to "eq.$uid"))
            if (existing == null) {
                backend.restPost<List<Profile>>(
                    "profiles",
                    mapOf(
                        "id" to uid,
                        "username" to username,
                        "email" to email,
                        "avatar_url" to "/imgs/profiles/onefree.png",
                        "banner_url" to "/imgs/profiles/default-free-banner.jpg",
                        "plan" to "free",
                        "role" to "member"
                    ),
                    upsert = true,
                    onConflict = "id"
                )
            }
        } catch (_: Exception) { }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = try {
        val s = backend.authSignIn(email.trim(), password)
        accessToken = s.accessToken
        session.saveSession(s)
        val uid = requireNotNull(s.user?.id) { "No user" }
        _state.value = SessionState(loading = false, userId = uid, profile = loadProfile(uid))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(mapAuthError(e))
    }

    /** Returns true when the account can sign in immediately (email already confirmed). */
    suspend fun signUp(email: String, password: String, username: String): Result<Boolean> = try {
        val res = backend.authSignUp(email.trim(), password, username.trim())
        val sess = res.session
        if (sess != null && sess.accessToken.isNotBlank()) {
            accessToken = sess.accessToken
            session.saveSession(sess)
            val uid = requireNotNull(sess.user?.id ?: res.user?.id)
            ensureProfile(uid, username.trim(), email.trim())
            _state.value = SessionState(loading = false, userId = uid, profile = loadProfile(uid))
            Result.success(true)
        } else {
            val uid = res.user?.id
            if (uid != null) {
                accessToken = null
                // Not signed in yet (email confirmation pending) — profile row may still be created by trigger.
            }
            Result.success(false)
        }
    } catch (e: Exception) {
        Result.failure(mapAuthError(e))
    }

    suspend fun signOut() {
        try {
            accessToken?.let { backend.authSignOut(it) }
        } catch (_: Exception) { }
        accessToken = null
        session.clearSession()
        _state.value = SessionState(loading = false)
        backend.close()
    }

    suspend fun recover(email: String): Result<Unit> = try {
        backend.authRecover(email.trim())
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(mapAuthError(e))
    }

    /** Completes password reset from the addonstation://recovery deep link tokens. */
    suspend fun completeRecovery(accessToken: String, refreshToken: String, newPassword: String): Result<Unit> = try {
        this.accessToken = accessToken
        backend.authUpdateUser(accessToken, newPassword)
        val sess = try {
            backend.authRefresh(refreshToken)
        } catch (_: Exception) { null }
        if (sess != null) {
            this.accessToken = sess.accessToken
            session.saveSession(sess)
            val uid = requireNotNull(sess.user?.id)
            _state.value = SessionState(loading = false, userId = uid, profile = loadProfile(uid))
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(mapAuthError(e))
    }

    fun signOutAsync() = scope.launch { signOut() }

    private fun mapAuthError(e: Exception): Exception {
        val m = e.message.orEmpty()
        return when {
            m.contains("Invalid login credentials", true) -> Exception("AUTH_INVALID")
            m.contains("Email not confirmed", true) -> Exception("AUTH_UNCONFIRMED")
            m.contains("already registered", true) || m.contains("already exists", true) -> Exception("AUTH_EXISTS")
            m.contains("Password", true) && m.contains("6", true) -> Exception("AUTH_PASS_LEN")
            e is BackendException && e.status == -1 -> Exception("AUTH_NETWORK")
            else -> e
        }
    }
}
