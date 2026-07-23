import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
	alias(libs.plugins.kotlinJvm)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

val appVersion = providers.gradleProperty("vnidrop.version").get()
val appVersionParts = appVersion.split(".")
require(
	appVersionParts.size == 3 &&
		appVersionParts.mapIndexed { index, part ->
			val number = part.toIntOrNull()
			number != null &&
				number.toString() == part &&
				number in (if (index == 0) 1 else 0)..65535
		}.all { it },
) {
	"vnidrop.version must be MAJOR.MINOR.PATCH with numeric components from 0 to 65535 and a non-zero major"
}

dependencies {
	implementation(projects.shared)

	implementation(compose.desktop.currentOs)
	implementation(libs.filekit.dialogs)
	implementation(libs.jna.platform)
	implementation(libs.kotlinx.coroutinesSwing)

	implementation(libs.compose.uiToolingPreview)
	testImplementation(libs.kotlin.testJunit)
}

compose.desktop {
	application {
		mainClass = "com.vnidrop.app.MainKt"
		buildTypes.release.proguard.isEnabled.set(false)

		nativeDistributions {
			targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
			packageName = "VniDrop"
			packageVersion = appVersion
			description = "Send files directly across your devices"
			vendor = "Sudosy Labs"
			licenseFile.set(project.file("../LICENSE"))
			windows {
				iconFile.set(project.file("../assets/windows/app-icon.ico"))
			}
			linux {
				packageName = "vnidrop"
				iconFile.set(project.file("../assets/linux/app-icon.png"))
				modules("jdk.security.auth")
				debMaintainer = "support@sudosy.fr"
				appRelease = "1"
				rpmLicenseType = "Apache-2.0"
			}
			fileAssociation(
				mimeType = "application/vnd.vnidrop.transfer",
				extension = "vnd",
				description = "VniDrop Invitation",
			)
		}
	}
}
