@file:OptIn(gobley.gradle.InternalGobleyGradleApi::class)

import gobley.gradle.cargo.dsl.appleMobile
import gobley.gradle.cargo.dsl.jvm
import gobley.gradle.GobleyHost
import gobley.gradle.rust.targets.RustAndroidTarget
import org.gradle.api.tasks.PathSensitivity
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
			implementation(libs.androidx.core.ktx)
			implementation(libs.google.code.scanner)
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
			implementation(libs.androidx.datastore)
			implementation(libs.androidx.datastore.preferences)
			implementation(libs.kotlinx.coroutinesCore)
			implementation(libs.qrcode.kotlin)
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
			implementation(libs.kotlinx.coroutinesTest)
		}
		jvmTest.dependencies {
			implementation(compose.desktop.currentOs)
			implementation(libs.compose.uiTest)
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
	builds.jvm {
		variants {
			// Desktop distributions are built per host. Do not publish disabled
			// cross-platform native jars into the app runtime classpath.
			embedRustLibrary.set(rustTarget == GobleyHost.current.rustTarget)
		}
	}
	builds.appleMobile {
		variants {
			buildTaskProvider.configure {
				if (rustTarget.cinteropName == "ios") {
					additionalEnvironment.put("IPHONEOS_DEPLOYMENT_TARGET", "16.0.0")
				}
			}
		}
	}
}

uniffi {
	generateFromLibrary {
		namespace = "vnidrop"
	}
}

tasks.configureEach {
	// Gobley does not currently treat every Rust source/API change as an input of
	// all platform cargo tasks. Without these inputs an incremental Android build
	// can package an older .so next to freshly generated UniFFI Kotlin bindings.
	if (name.startsWith("cargoBuild")) {
		inputs.files(
			fileTree(layout.projectDirectory.dir("../crates/vnidrop")) {
				include("Cargo.toml", "build.rs", "src/**/*.rs")
			},
			layout.projectDirectory.file("../Cargo.toml"),
			layout.projectDirectory.file("../Cargo.lock"),
		).withPathSensitivity(PathSensitivity.RELATIVE)
	}
	if (name.contains("Linux") || name.contains("MinGW") || name.contains("MacOSX64")) {
		enabled = false
	}
}
