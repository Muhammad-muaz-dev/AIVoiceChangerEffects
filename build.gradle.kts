plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false

    // Updated to latest stable for compatibility with Kotlin 2.x
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // Matching KSP for Kotlin 2.0.21
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false

    id("com.google.dagger.hilt.android") version "2.52" apply false
}
