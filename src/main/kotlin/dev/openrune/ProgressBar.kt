package dev.openrune

class ProgressBar(private val total: Int) {
    private var current = 0
    var extraMessage : String = ""


    @Synchronized
    fun update() {
        current++
        val percentage = current * 100 / total
        val progress = current * 50 / total
        val bar = "=".repeat(progress) + " ".repeat(50 - progress)
        print("\r[$bar] $percentage% ($current/$total) $extraMessage")
        System.out.flush()  // Ensure the update is immediately printed
    }
}