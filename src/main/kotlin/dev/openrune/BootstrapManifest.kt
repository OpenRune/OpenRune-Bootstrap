package dev.openrune

data class BootstrapManifest(
    val launcherArguments : Array<String> = emptyArray(),
    val launcherJvm11Arguments : Array<String> = emptyArray(),
    val launcherJvm11WindowsArguments : Array<String> = emptyArray(),
    val launcherJvm17Arguments : Array<String> = emptyArray(),
    val launcherJvm17MacArguments : Array<String> = emptyArray(),
    val launcherJvm17WindowsArguments : Array<String> = emptyArray(),
    val clientJvmArguments : Array<String> = emptyArray(),
    val clientJvm9Arguments : Array<String> = emptyArray(),
    val clientJvm17MacArguments : Array<String> = emptyArray(),
    val clientJvm17Arguments : Array<String> = emptyArray(),
    var artifacts : Array<Artifacts> = emptyArray(),
    var updates : Array<Updates> = emptyArray(),
    var dependencyHashes : Map<String,String> = emptyMap<String, String>().toMutableMap()
) {

    data class Platform(val arch: String?, val name: String?)

    data class Artifacts(
        val hash : String = "",
        val name : String = "",
        val path : String = "",
        val size : Long = -1,
        val platform : List<Platform>? = null
    )

    data class Updates(
        val arch : String = "",
        val hash : String = "",
        val minimumVersion : String = "",
        val name : String = "",
        val os : String = "",
        val rollout : Int = -1,
        val size : Int = -1,
        val url : String = "",
        val version : String = ""
    )

}