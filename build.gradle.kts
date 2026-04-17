plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false

    // ✅ Updated Kotlin
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false

    // ✅ Matching KSP
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false

    id("com.google.dagger.hilt.android") version "2.57.1" apply false
}