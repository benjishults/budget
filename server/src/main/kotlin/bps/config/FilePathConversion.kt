package bps.config

fun convertToPath(path: String) =
    path.replaceFirst(Regex("^~/"), "${System.getProperty("user.home")}/")
