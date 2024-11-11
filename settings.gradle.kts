rootProject.name = "budget"

plugins {
    id("com.gradle.develocity") version ("3.18.1")
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}

