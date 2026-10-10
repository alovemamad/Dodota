// Top-level build file where you can add configuration options common to all sub-projects/modules.
import java.util.Base64

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Automatically recreate debug.keystore from debug.keystore.base64 when opened locally in Android Studio
val debugKeystoreFile = rootProject.file("debug.keystore")
val debugKeystoreBase64File = rootProject.file("debug.keystore.base64")
if (!debugKeystoreFile.exists() && debugKeystoreBase64File.exists()) {
  runCatching {
    val base64Content = debugKeystoreBase64File.readText(Charsets.UTF_8).trim()
    val decodedBytes = Base64.getMimeDecoder().decode(base64Content)
    debugKeystoreFile.writeBytes(decodedBytes)
  }
}

