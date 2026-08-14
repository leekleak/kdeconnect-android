import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    dependencies {
        classpath(libs.android.gradlePlugin)
        classpath(libs.kotlin.gradlePlugin)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.koin)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.aboutLibraries)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-XXLanguage:+PropertyParamAnnotationDefaultTargetMode",
            "-XXLanguage:+ExplicitBackingFields"
        )
    }
}

android {
    namespace = "org.kde.kdeconnect_tp"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.kde.kdeconnect_tp"
        minSdk = 26
        targetSdk = 37
        versionCode = 13509
        versionName = "1.35.9"
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            merges += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE.md")
        }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("com.albertvaka.classindexksp.annotations", "org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin")
}

androidComponents {
    onVariants { variant ->
        // When the "Generate Signed APK/Bundle" wizard is used, copy the source map to the output directory
        val variantName = variant.name
        val capitalized = variantName.replaceFirstChar { it.uppercase() }
        // The 'android.injected.apk.location' property is only set when using the wizard
        val apkLocation = providers.gradleProperty("android.injected.apk.location")
        // Plain task with doLast (no declared outputs) so we don't clash with AGP tasks that also write into the
        // destination folder (e.g. createReleaseApkListingFileRedirect writing output-metadata.json).
        val mappingFile = layout.buildDirectory.file("outputs/mapping/$variantName/mapping.txt")
        val nativeSymbolsFile = layout.buildDirectory.file("outputs/native-debug-symbols/$variantName/native-debug-symbols.zip")
        val destDir = apkLocation.map { File(it, variantName) }
        val copyExtras = tasks.register("copySigningExtraOutputs$capitalized") {
            description = "Copies R8 mapping.txt and native-debug-symbols.zip next to the signed $variantName APK/bundle."
            onlyIf { apkLocation.isPresent }
            doLast {
                val dest = destDir.get().apply { mkdirs() }
                val mapping = mappingFile.get().asFile
                if (mapping.exists()) mapping.copyTo(File(dest, "mapping.txt"), overwrite = true)
                val symbols = nativeSymbolsFile.get().asFile
                if (symbols.exists()) symbols.copyTo(File(dest, "native-debug-symbols.zip"), overwrite = true)
            }
        }
        tasks.matching { it.name == "assemble$capitalized" || it.name == "bundle$capitalized" }
            .configureEach { finalizedBy(copyExtras) }
    }
}

dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.palette)

    debugImplementation(libs.androidx.compose.ui.tooling)
    
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.navigation3)
    implementation(libs.koin.annotations)

    implementation(libs.google.android.material)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.handroid)

    implementation(libs.apache.sshd.core)
    implementation(libs.apache.sshd.sftp)
    implementation(libs.apache.sshd.scp)

    implementation(libs.bcpkix.jdk18on) //For SSL certificate generation

    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)

    ksp(libs.classindexksp)

    // The android-smsmms library is the only way I know to handle MMS in Android
    // (Shouldn't a phone OS make phone things easy?)
    // This library was originally authored as com.klinkerapps at https://github.com/klinker41/android-smsmms.
    // However, that version is under-loved. I have therefore made "some fixes" and published it.
    // Please see https://invent.kde.org/sredman/android-smsmms/-/tree/master
    implementation(libs.android.smsmms)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Blur
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)

    // Coil
    implementation(libs.coil.compose)

    // Testing
    implementation(libs.kermit)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.jsonassert)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    // For device controls
    implementation(libs.kotlinx.coroutines.jdk9)

    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.core)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

aboutLibraries {
    collect {
        fetchRemoteLicense = true // Required for bouncy castle, otherwise the license is not detected
    }
}