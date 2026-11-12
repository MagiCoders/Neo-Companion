pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // sherpa-onnx (offline voice test) is only published on JitPack. Exclusive both ways: that
    // group only from JitPack, and nothing else from JitPack.
    exclusiveContent {
      forRepository { maven("https://jitpack.io") }
      filter { includeGroup("com.github.k2-fsa.sherpa-onnx") }
    }
  }
}

rootProject.name = "MochiLife"

include(":app")
