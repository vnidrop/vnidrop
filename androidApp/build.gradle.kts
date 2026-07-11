import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.androidApplication)
	alias(libs.plugins.kotlinAndroid)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_11
	}
}
dependencies {
	implementation(projects.shared)

	implementation(libs.androidx.activity.compose)

	implementation(libs.compose.uiToolingPreview)
	debugImplementation(libs.compose.uiTooling)
}

android {
	namespace = "com.vnidrop.app"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "com.vnidrop.app"
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()
		versionCode = 1
		versionName = "1.0"
	}
	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
		jniLibs {
			pickFirsts += "lib/arm64-v8a/libvnidrop.so"
		}
	}
	buildTypes {
		getByName("release") {
			isMinifyEnabled = false
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	sourceSets {
		getByName("debug") {
			jniLibs.srcDir(project(":shared").layout.buildDirectory.dir("intermediates/rust/aarch64-linux-android/debug"))
		}
	}
}

tasks.configureEach {
	if (name == "mergeDebugJniLibFolders" || name == "mergeDebugNativeLibs") {
		dependsOn(":shared:copyAndroidAndroidArm64Debug")
	}
}
