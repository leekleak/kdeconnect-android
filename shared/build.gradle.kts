plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.koin)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.kde.kdeconnect.generated.resources"
}

kotlin {
    applyDefaultHierarchyTemplate()
    android {
        namespace = "org.kde.kdeconnect"
        compileSdk = 37
        minSdk = 26
        
        androidResources {
            enable = true
        }

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.koin.annotations)
                implementation(libs.androidx.room3.runtime)
                implementation(libs.kermit)
                implementation(libs.components.resources)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.bcpkix.jdk18on)
                implementation(libs.slf4j.api)
                implementation(libs.slf4j.handroid)
                implementation(libs.koin.android)
                
                implementation(libs.androidx.compose.ui)
                implementation(libs.androidx.compose.foundation)
                implementation(libs.androidx.compose.material3)
                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.palette)
                implementation(libs.androidx.media3.session)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.documentfile)
                implementation(libs.androidx.lifecycle.viewmodel.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.navigation3.runtime)
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)
                implementation(libs.koin.androidx.compose)
                implementation(libs.koin.compose.navigation3)
                implementation(libs.google.android.material)
                implementation(libs.apache.sshd.core)
                implementation(libs.apache.sshd.sftp)
                implementation(libs.apache.sshd.scp)
                implementation(libs.android.smsmms)
                implementation(libs.haze)
                implementation(libs.haze.blur)
                implementation(libs.haze.blur.materials)
                implementation(libs.coil.compose)
                implementation(libs.kotlinx.coroutines.jdk9)
                implementation(libs.aboutlibraries.core)
                implementation(libs.aboutlibraries.compose)
                implementation(libs.aboutlibraries.compose.core)
                implementation(libs.reorderable)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.jsonassert)
                implementation(libs.robolectric)
                implementation(libs.androidx.junit)
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.kermit)
                implementation(libs.slf4j.simple)
            }
        }
    }
}

dependencies {
    ksp(libs.androidx.room3.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

aboutLibraries {
    collect {
        fetchRemoteLicense = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/commonMain/composeResources")
    }
}
