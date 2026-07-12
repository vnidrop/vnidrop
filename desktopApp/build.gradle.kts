import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
	alias(libs.plugins.kotlinJvm)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

dependencies {
	implementation(projects.shared)

	implementation(compose.desktop.currentOs)
	implementation(libs.kotlinx.coroutinesSwing)
	implementation(libs.jna)

	implementation(libs.compose.uiToolingPreview)
	testImplementation(libs.kotlin.testJunit)
}

compose.desktop {
	application {
		mainClass = "com.vnidrop.app.MainKt"

		nativeDistributions {
			targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
			packageName = "com.vnidrop.app"
			packageVersion = "1.0.0"
			macOS {
				iconFile.set(project.file("../assets/macos/app-icon.icns"))
			}
			windows {
				iconFile.set(project.file("../assets/windows/app-icon.ico"))
			}
			linux {
				iconFile.set(project.file("../assets/linux/app-icon.png"))
			}
			fileAssociation(
				mimeType = "application/vnd.vnidrop.transfer",
				extension = "vnd",
				description = "VniDrop Invitation",
			)
		}
	}
}
