import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.TextReportRenderer
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
    alias(libs.plugins.dependencyLicenseReport)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.koin)
}

val licenseResDir = "$projectDir/build/dependency-license-res"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
}

android {
    namespace = "org.kde.kdeconnect_tp"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.kde.kdeconnect_tp"
        minSdk = 26
        targetSdk = 36
        versionCode = 13509
        versionName = "1.35.9"
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main") {
        res.directories += licenseResDir
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            merges += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/NOTICE")
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
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
    // It has a bug that causes a crash when using PosixFilePermission and minSdk < 26.
    // It has been used in SSHD Core.
    // We have taken a workaround to fix it.
    // See `FixPosixFilePermissionClassVisitorFactory` for more details.
    coreLibraryDesugaring(libs.android.desugarJdkLibsNio)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.constraintlayout.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.common.java8)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.ui.viewbinding)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.navigation3)
    implementation(libs.koin.annotations)

    implementation(libs.androidx.gridlayout)
    implementation(libs.google.android.material)
    implementation(libs.disklrucache) //For caching album art bitmaps. FIXME: Not updated in 10+ years. Replace with Kache.
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.handroid)

    implementation(libs.apache.sshd.core)
    implementation(libs.apache.sshd.sftp)
    implementation(libs.apache.sshd.scp)

    implementation(libs.bcpkix.jdk15on) //For SSL certificate generation

    ksp(libs.classindexksp)

    // The android-smsmms library is the only way I know to handle MMS in Android
    // (Shouldn't a phone OS make phone things easy?)
    // This library was originally authored as com.klinkerapps at https://github.com/klinker41/android-smsmms.
    // However, that version is under-loved. I have therefore made "some fixes" and published it.
    // Please see https://invent.kde.org/sredman/android-smsmms/-/tree/master
    implementation(libs.android.smsmms)
    implementation(libs.logger)

    implementation(libs.commons.io)
    implementation(libs.commons.collections4)
    implementation(libs.commons.lang3)

    implementation(libs.univocity.parsers)

    // Kotlin
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Blur
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // Coil
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.slf4j.simple) // do not try to use the Android logger backend in tests
    testImplementation(libs.jsonassert)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    // For device controls
    implementation(libs.kotlinx.coroutines.jdk9)
}

licenseReport {
    configurations = LicenseReportExtension.ALL
    renderers = arrayOf<ReportRenderer>(TextReportRenderer())
}

tasks.named("generateLicenseReport") {
    val outputFile = file("$licenseResDir/raw/license")
    val inputFiles = files(
        layout.projectDirectory.file("COPYING"),
        layout.buildDirectory.file("reports/dependency-license/THIRD-PARTY-NOTICES.txt")
    )
    outputs.file(outputFile)
    doLast {
        outputFile.apply {
            parentFile.mkdirs()
            writeText(inputFiles.joinToString(separator = "\n") { it.readText() })
        }
    }
}

tasks.named("preBuild") {
    dependsOn("generateLicenseReport")
}
