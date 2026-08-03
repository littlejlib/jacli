rootProject.name = "dyn"

// Computed includes: nothing textual can read this, which is what the glob fallback is for.
file("modules").listFiles()?.forEach { include(it.name) }
