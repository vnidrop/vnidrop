@file:OptIn(gobley.gradle.InternalGobleyGradleApi::class)

import gobley.gradle.rust.targets.RustAndroidTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidLibrary)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
	alias(libs.plugins.gobleyCargo)
	alias(libs.plugins.gobleyUniffi)
	alias(libs.plugins.kotlinAtomicfu)
}

kotlin {
	listOf(
		iosArm64(),
		iosSimulatorArm64()
	).forEach { iosTarget ->
		iosTarget.binaries.framework {
			baseName = "Shared"
			isStatic = true
		}
	}

	androidTarget {
		compilerOptions {
			jvmTarget = JvmTarget.JVM_11
		}
	}

	jvm()

	sourceSets {
		androidMain.dependencies {
			implementation(libs.androidx.activity.compose)
			implementation(libs.compose.uiToolingPreview)
		}
		commonMain.dependencies {
			implementation(libs.compose.runtime)
			implementation(libs.compose.foundation)
			implementation(libs.compose.material3)
			implementation(libs.compose.ui)
			implementation(libs.compose.components.resources)
			implementation(libs.compose.uiToolingPreview)
			implementation(libs.androidx.lifecycle.viewmodelCompose)
			implementation(libs.androidx.lifecycle.runtimeCompose)
			implementation(libs.kotlinx.coroutinesCore)
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
		}
	}
}

dependencies {
	debugImplementation(libs.compose.uiTooling)
}

android {
	namespace = "com.vnidrop.app.shared"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
}

cargo {
	packageDirectory = layout.projectDirectory.dir("../crates/vnidrop")
	publishJvmArtifacts = true
	androidTargetsToBuild.set(setOf(RustAndroidTarget.Arm64))
}

uniffi {
	generateFromLibrary {
		namespace = "vnidrop"
	}
}

tasks.configureEach {
	if (name.contains("Linux") || name.contains("MinGW") || name.contains("MacOSX64")) {
		enabled = false
	}
}
