package com.jcasadomolina.airpass

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val ALIAS = "airpass-huella"
private const val PREFERENCIAS = "airpass"
private const val CLAVE_URI = "huellaUri"
private const val CLAVE_DATOS = "huellaDatos"
private const val TRANSFORMACION = "AES/GCM/NoPadding"

/** Las preferencias privadas de AirPass, donde se guarda la clave compuesta ya cifrada. */
private fun preferencias(contexto: Context) = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

/** true si el dispositivo tiene Android 11 o posterior y una biometría fuerte, como la huella, configurada. */
fun biometriaDisponible(contexto: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        contexto.getSystemService(BiometricManager::class.java).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/** true si la huella está activada para esta base. Solo una base a la vez puede tenerla. */
fun tieneHuella(contexto: Context, uri: Uri): Boolean = preferencias(contexto).getString(CLAVE_URI, null) == uri.toString()

/** Desactiva la huella: borra la clave compuesta cifrada y la clave del Keystore que la descifraba. */
fun olvidarHuella(contexto: Context) {
    preferencias(contexto).edit().remove(CLAVE_URI).remove(CLAVE_DATOS).apply()
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
}

/**
 * Clave AES del Android Keystore. No sale nunca del hardware seguro del dispositivo, solo se puede usar justo
 * después de reconocer una huella, y queda invalidada si alguien añade otra huella al dispositivo.
 */
@RequiresApi(Build.VERSION_CODES.R)
private fun claveDelKeystore(): SecretKey {
    val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (almacen.getKey(ALIAS, null) as SecretKey?)?.let { return it }
    val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generador.init(
        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build(),
    )
    return generador.generateKey()
}

/**
 * Muestra el diálogo de huella del sistema atado al cifrador: el Keystore solo deja usar ese cifrador si la huella
 * se reconoce. Devuelve el cifrador ya autorizado, o null si el usuario cancela o el sistema da un error.
 */
@RequiresApi(Build.VERSION_CODES.R)
private suspend fun pedirHuella(contexto: Context, titulo: String, cifrador: Cipher): Cipher? =
    suspendCancellableCoroutine { continuacion ->
        val cancelar = CancellationSignal()
        continuacion.invokeOnCancellation { cancelar.cancel() }
        fun terminar(resultado: Cipher?) {
            if (continuacion.isActive) continuacion.resume(resultado)
        }
        BiometricPrompt.Builder(contexto)
            .setTitle(titulo)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButton("Cancelar", contexto.mainExecutor) { _, _ -> terminar(null) }
            .build()
            .authenticate(
                BiometricPrompt.CryptoObject(cifrador),
                cancelar,
                contexto.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) =
                        terminar(resultado.cryptoObject.cipher)

                    override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) = terminar(null)
                },
            )
    }

/**
 * Activa la huella para una base: tras reconocer la huella, cifra su clave compuesta con la clave del Keystore y
 * la guarda en las preferencias privadas. Nunca se guarda la contraseña. Devuelve false si el usuario cancela.
 */
@RequiresApi(Build.VERSION_CODES.R)
suspend fun guardarHuella(contexto: Context, uri: Uri, compuesta: ByteArray): Boolean {
    val cifrador = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, claveDelKeystore()) }
    val autorizado = pedirHuella(contexto, "Activar el desbloqueo con huella", cifrador) ?: return false
    val cifrada = autorizado.doFinal(compuesta)
    val datos = Base64.encodeToString(autorizado.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cifrada, Base64.NO_WRAP)
    preferencias(contexto).edit().putString(CLAVE_URI, uri.toString()).putString(CLAVE_DATOS, datos).apply()
    return true
}

/**
 * Pide la huella y descifra la clave compuesta guardada; null si el usuario cancela. Lanza una
 * GeneralSecurityException si la clave ya no sirve, por ejemplo porque se añadió otra huella al dispositivo.
 */
@RequiresApi(Build.VERSION_CODES.R)
suspend fun leerHuella(contexto: Context): ByteArray? {
    val (iv, cifrada) = checkNotNull(preferencias(contexto).getString(CLAVE_DATOS, null))
        .split(":").map { Base64.decode(it, Base64.NO_WRAP) }
    val cifrador = Cipher.getInstance(TRANSFORMACION).apply {
        init(Cipher.DECRYPT_MODE, claveDelKeystore(), GCMParameterSpec(128, iv))
    }
    val autorizado = pedirHuella(contexto, "Abrir con huella", cifrador) ?: return null
    return autorizado.doFinal(cifrada)
}
