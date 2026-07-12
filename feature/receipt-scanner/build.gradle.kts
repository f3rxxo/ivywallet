plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.receiptscanner"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)

    // For MerchantCategoryOverrideStore (user-taught merchant->category rules),
    // piggybacking on the app's existing Preferences DataStore setup.
    // (Already exposed transitively via shared.data.core's `api(libs.datastore)`,
    // but declared explicitly here for clarity.)
    implementation(libs.datastore)

    // ML Kit on-device text recognition (free, offline, no API key)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Coroutines <-> ML Kit Task interop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation(projects.shared.ui.testing)
}
