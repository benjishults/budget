package bps.budget

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform