rootProject.name = "budget"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

//plugins {
//    id("com.gradle.develocity") version ("3.18.1")
//}
//
//develocity {
//    buildScan {
//        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
//        termsOfUseAgree.set("yes")
////        publishing.onlyIf {
////            !System.getenv("CI").isNullOrEmpty()
////// && it.buildResult.failures.isNotEmpty()
////        }
//    }
//}

include(":composeApp")
include(":server")
include(":shared")
