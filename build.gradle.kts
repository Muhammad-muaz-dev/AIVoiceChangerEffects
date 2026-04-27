plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false

    // ✅ Updated Kotlin
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false

    // ✅ Matching KSP
    id("com.google.devtools.ksp") version "2.3.2" apply false

    id("com.google.dagger.hilt.android") version "2.57.1" apply false
}