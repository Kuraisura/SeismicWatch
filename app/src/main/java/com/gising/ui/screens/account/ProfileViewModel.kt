package com.gising.ui.screens.account

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gising.data.repository.CircleRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Backs the editable profile screen and the home avatar. Reads the signed-in identity from
 * FirebaseAuth and lets the user rename themselves; saving updates the Auth profile, the private
 * `users/{uid}` document, and (best-effort) the denormalized name family see in shared circles.
 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    data class ProfileUi(
        val displayName: String = "",
        val email: String = "",
        val photoUrl: String = "",
        val saving: Boolean = false,
        /** True while the picked photo is uploading to Storage. */
        val uploadingPhoto: Boolean = false,
        /** Non-zero when a save just succeeded; the screen watches this to confirm. */
        val savedAt: Long = 0L,
        val error: String? = null,
    ) {
        /** First letter of the name (or email), for the avatar fallback. */
        val initial: String
            get() = (displayName.trim().firstOrNull() ?: email.trim().firstOrNull())
                ?.uppercaseChar()?.toString() ?: "?"
    }

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val circles = CircleRepository()

    private val _ui = MutableStateFlow(ProfileUi())
    val ui: StateFlow<ProfileUi> = _ui.asStateFlow()

    init { reload() }

    /**
     * Pull the latest identity from FirebaseAuth into the form, then overlay the avatar stored in
     * Firestore — which may be a base64 `data:` URI that can't live on the Auth profile.
     */
    fun reload() {
        val u = auth.currentUser
        _ui.update {
            it.copy(
                displayName = u?.displayName.orEmpty(),
                email = u?.email.orEmpty(),
                photoUrl = u?.photoUrl?.toString().orEmpty(),
                error = null,
            )
        }
        val uid = u?.uid ?: return
        viewModelScope.launch {
            runCatching { firestore.collection("users").document(uid).get().await() }
                .getOrNull()?.let { snap ->
                    val storedPhoto = snap.getString("photoUrl").orEmpty()
                    val storedName = snap.getString("displayName").orEmpty()
                    _ui.update {
                        it.copy(
                            photoUrl = storedPhoto.ifEmpty { it.photoUrl },
                            displayName = it.displayName.ifEmpty { storedName },
                        )
                    }
                }
        }
    }

    fun onNameChange(value: String) = _ui.update { it.copy(displayName = value, error = null) }

    /**
     * Compress a freshly picked avatar to a small base64 `data:` URI and persist it to the private
     * profile doc + the denormalized circle membership (so family see the new picture). Stored in
     * Firestore only — no Firebase Storage (paid) and NOT on the Auth profile (a data URI exceeds
     * Auth's photoUri length limit). Runs independently of the name "Save changes" button.
     */
    fun onPhotoPicked(uri: Uri) {
        val user = auth.currentUser ?: return
        _ui.update { it.copy(uploadingPhoto = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val dataUri = withContext(Dispatchers.IO) { encodeAvatar(getApplication(), uri) }
                    ?: error("Couldn't read that image")
                firestore.collection("users").document(user.uid).set(
                    mapOf("photoUrl" to dataUri, "updatedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge(),
                ).await()
                val name = _ui.value.displayName.trim().ifEmpty { user.displayName.orEmpty() }
                circles.updateMyDisplayName(name, dataUri)
                dataUri
            }.onSuccess { dataUri ->
                _ui.update {
                    it.copy(uploadingPhoto = false, photoUrl = dataUri, savedAt = System.currentTimeMillis())
                }
            }.onFailure { e ->
                _ui.update { it.copy(uploadingPhoto = false, error = e.message ?: "Couldn't set photo") }
            }
        }
    }

    fun save() {
        val user = auth.currentUser ?: return
        val name = _ui.value.displayName.trim()
        if (name.isEmpty()) {
            _ui.update { it.copy(error = "Name can't be empty") }
            return
        }
        val photo = _ui.value.photoUrl.trim()
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        // Only real URLs go on the Auth profile; a base64 data URI is too long for it
                        // (it lives in Firestore instead).
                        .apply { if (photo.isNotEmpty() && !photo.startsWith("data:")) setPhotoUri(Uri.parse(photo)) }
                        .build(),
                ).await()
                firestore.collection("users").document(user.uid).set(
                    mapOf(
                        "displayName" to name,
                        "photoUrl" to photo,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
                circles.updateMyDisplayName(name, photo)
            }.onSuccess {
                _ui.update {
                    it.copy(saving = false, savedAt = System.currentTimeMillis(), displayName = name)
                }
            }.onFailure { e ->
                _ui.update { it.copy(saving = false, error = e.message ?: "Couldn't save profile") }
            }
        }
    }
}

/**
 * Decode, downscale (long edge ≤ [maxSize] px) and JPEG-compress the picked image, then base64-encode
 * it as a `data:image/jpeg;base64,...` URI. At 256px / quality 70 this is ~15–40 KB — comfortably
 * under Firestore's 1 MB document limit. Returns null if the image can't be read.
 */
private fun encodeAvatar(context: Context, uri: Uri, maxSize: Int = 256, quality: Int = 70): String? {
    val resolver = context.contentResolver
    // First pass: read just the bounds so we can sub-sample large photos cheaply.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    var sample = 1
    while (srcW / sample > maxSize * 2 || srcH / sample > maxSize * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

    val longEdge = maxOf(decoded.width, decoded.height).toFloat()
    val scale = if (longEdge > maxSize) maxSize / longEdge else 1f
    val bmp = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else decoded

    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
}
