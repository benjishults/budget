rootProject.name = "budget"

plugins {
    id("com.gradle.develocity") version ("3.18.1")
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
//        publishing.onlyIf {
//            !System.getenv("CI").isNullOrEmpty()
//// && it.buildResult.failures.isNotEmpty()
//        }
    }
}

