package io.github.noahhhx.mimos.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.noahhhx.mimos.session.StoredSession
import io.github.noahhhx.mimos.session.TokenStore
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.sessionData: DataStore<Preferences> by
    preferencesDataStore(
        name = "session",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
private val ID_TOKEN = stringPreferencesKey("id_token")

/**
 * The refresh and id tokens in DataStore, each encrypted under a non-exportable Android Keystore
 * AES-GCM key. The file is excluded from backups, since a restored copy cannot be opened without
 * the key; anything that does not open reads as [StoredSession.Unreadable].
 */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val data = context.applicationContext.sessionData

    override suspend fun load(): StoredSession =
        withContext(Dispatchers.IO) {
            val stored =
                try {
                    data.data.first()
                } catch (_: java.io.IOException) {
                    return@withContext StoredSession.Unreadable
                }
            val sealedRefresh = stored[REFRESH_TOKEN] ?: return@withContext StoredSession.None
            try {
                StoredSession.Present(
                    refreshToken = open(REFRESH_TOKEN.name, sealedRefresh),
                    idToken = stored[ID_TOKEN]?.let { open(ID_TOKEN.name, it) },
                )
            } catch (_: GeneralSecurityException) {
                StoredSession.Unreadable
            } catch (_: IllegalArgumentException) {
                StoredSession.Unreadable
            }
        }

    override suspend fun save(session: StoredSession.Present) {
        withContext(Dispatchers.IO) {
            val refresh = seal(REFRESH_TOKEN.name, session.refreshToken)
            val id = session.idToken?.let { seal(ID_TOKEN.name, it) }
            data.edit {
                it[REFRESH_TOKEN] = refresh
                if (id != null) it[ID_TOKEN] = id else it.remove(ID_TOKEN)
            }
        }
    }

    override suspend fun clear() {
        data.edit { it.clear() }
    }

    private fun seal(name: String, plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyOrCreate())
        cipher.updateAAD(name.toByteArray())
        val encrypted = cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun open(name: String, sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            existingKey() ?: throw GeneralSecurityException("no key"),
            GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
        )
        cipher.updateAAD(name.toByteArray())
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    private fun existingKey(): SecretKey? =
        (KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(KEY_ALIAS, null)) as? SecretKey

    private fun keyOrCreate(): SecretKey =
        existingKey()
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                }
                .generateKey()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mimos.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
