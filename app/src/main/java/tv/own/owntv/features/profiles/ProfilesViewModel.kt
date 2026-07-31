package tv.own.owntv.features.profiles

import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.util.Pin
import tv.own.owntv.features.settings.data.SettingsRepository

/**
 * Phase 6.5 — profile creation/switching and the launch gate's data. Shared by the "Who's watching?"
 * gate and the Settings → Profiles management screen.
 */
class ProfilesViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val openSubtitlesAccounts: tv.own.owntv.core.subtitles.OpenSubtitlesAccountManager,
) : ViewModel() {

    // Eagerly on purpose: MainActivity's splash gate blocks the first frame on this list, so the
    // query has to start when the ViewModel is built, not on first collection inside composition.
    // Measured: WhileSubscribed here cost ~1.3s of cold start.
    val profiles: StateFlow<List<ProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Make [profile] active (routes the app into the shell) once the preference write commits. */
    fun switchTo(profile: ProfileEntity, onSwitched: () -> Unit = {}) {
        viewModelScope.launch {
            settings.setActiveProfile(profile.id)
            onSwitched()
        }
    }

    fun verifyPin(profile: ProfileEntity, pin: String): Boolean = Pin.verify(pin, profile.pinHash)

    /**
     * Create a new profile. New profiles inherit the existing sources (single-account, multi-viewer)
     * so they immediately have content; favorites/history stay per-profile.
     */
    fun create(name: String, avatarId: Int, isKids: Boolean, pin: String?, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = profileDao.insert(
                ProfileEntity(
                    name = name.ifBlank { "Profile" },
                    avatarColor = 0,
                    avatarId = avatarId,
                    isKids = isKids,
                    pinHash = pin?.takeIf { it.isNotBlank() }?.let { Pin.hash(it) },
                ),
            )
            // Link every existing source to the new profile.
            sourceDao.observeForProfileOnceLinked(id)
            onCreated(id)
        }
    }

    /** Apply edits from the editor dialog. [pin]: null = keep the existing PIN, "" = remove it. */
    fun edit(profile: ProfileEntity, name: String, avatarId: Int, isKids: Boolean, pin: String?) {
        viewModelScope.launch {
            val pinHash = when {
                pin == null -> profile.pinHash
                pin.isEmpty() -> null
                else -> Pin.hash(pin)
            }
            profileDao.update(profile.copy(name = name.ifBlank { profile.name }, avatarId = avatarId, isKids = isKids, pinHash = pinHash))
        }
    }

    fun rename(profile: ProfileEntity, name: String) {
        viewModelScope.launch { profileDao.update(profile.copy(name = name.ifBlank { profile.name })) }
    }

    fun setKids(profile: ProfileEntity, isKids: Boolean) {
        viewModelScope.launch { profileDao.update(profile.copy(isKids = isKids)) }
    }

    fun setPin(profile: ProfileEntity, pin: String?) {
        viewModelScope.launch { profileDao.setPin(profile.id, pin?.takeIf { it.isNotBlank() }?.let { Pin.hash(it) }) }
    }

    fun delete(profile: ProfileEntity) {
        viewModelScope.launch {
            // Never delete the last profile.
            if (profileDao.count() <= 1) return@launch
            val activeProfileId = settings.activeProfileId.first()
            val remainingProfileId = profileDao.getAllOnce().firstOrNull { it.id != profile.id }?.id
            runCatching { launcherIntegrationRepository.clearProfile(profile.id) }
            // Deleting a profile permanently erases its stored OpenSubtitles login (subtitle plan §5.5).
            openSubtitlesAccounts.eraseFor(profile.id)
            profileDao.delete(profile)
            if (activeProfileId == profile.id) {
                settings.setActiveProfile(remainingProfileId ?: -1L)
            }
        }
    }
}

/** Links all currently-known sources to a freshly created profile (helper kept off the entity API). */
private suspend fun SourceDao.observeForProfileOnceLinked(profileId: Long) {
    // All sources currently belong to existing profiles; share them with the new one.
    val allSourceIds = allSourceIds()
    allSourceIds.forEach { link(ProfileSourceCrossRef(profileId = profileId, sourceId = it)) }
}
