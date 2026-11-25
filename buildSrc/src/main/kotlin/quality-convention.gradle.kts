import com.android.build.api.dsl.LibraryExtension
import io.gitlab.arturbosch.detekt.Detekt

plugins {
  id("io.gitlab.arturbosch.detekt")
  id("org.jetbrains.kotlinx.kover")
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
  source.setFrom("src/main/java")
}

tasks.withType<Detekt>().configureEach {
  reports {
    html.required = true
    xml.required = true
    md.required = false
    sarif.required = false
    txt.required = false
  }
}

pluginManager.withPlugin("com.android.library") {
  extensions.configure<LibraryExtension> {
    lint {
      abortOnError = true
      warningsAsErrors = true
      checkDependencies = false
    }
  }
}

kover { currentProject { createVariant("coverage") { add("debug") } } }
