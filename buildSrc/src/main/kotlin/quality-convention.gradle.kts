/*
 * Copyright 2025 by Patryk Goworowski and Patrick Michalik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
  id("com.android.lint")
  id("io.gitlab.arturbosch.detekt")
  id("org.jetbrains.kotlinx.kover")
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
  source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin")
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

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
  extensions.configure<KotlinMultiplatformExtension> {
    targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
      lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        // androidx.lifecycle ships a lint.jar built against an older lint API; lint skips it.
        disable += "ObsoleteLintCustomCheck"
      }
    }
  }
}

kover {
  reports {
    filters { excludes { classes("*ComposableSingletons*") } }
    variant("android") { verify { rule { minBound(80) } } }
  }
}
