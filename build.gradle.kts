import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
	id("org.jetbrains.kotlin.jvm")
	id("org.jetbrains.intellij.platform")
	id("org.jetbrains.changelog")
}
intellijPlatform {
	pluginConfiguration {
		id = "jp.ac.nitech.cc.hop.migration"
		name = "Hop Migration Assistant"
		id.set(providers.gradleProperty("pluginId"))
		name.set(providers.gradleProperty("pluginName"))
		version.set(providers.gradleProperty("version"))
		description.set(providers.gradleProperty("pluginDescription"))

		vendor {
			name.set(providers.gradleProperty("pluginVendor"))
		}
	}
}
dependencies {
	testImplementation("junit:junit:4.13.2")
	implementation(libs.jetbrains.annotations)

	// IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
	intellijPlatform {
		intellijIdea(libs.versions.intellij.idea.get())
		bundledPlugin("com.intellij.java")
		testFramework(TestFrameworkType.Platform)
	}
}
tasks.withType<Copy>().configureEach {
	inputs.property("file.encoding", "UTF-8")
	filteringCharset = "UTF-8"
}
tasks.named<Zip>("buildPlugin") {
	group = "1-release"
	destinationDirectory.set(layout.projectDirectory.dir("dist"))
}
tasks.named("runIde") {
	group = "2-develop"
}