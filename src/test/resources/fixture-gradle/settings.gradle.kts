pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "fixture-gradle"

include("core")
include(
    "tools:cli",
)

// include("dead")  -- deliberately commented out: a textual reader that misses this indexes a
// directory the build has abandoned, which is the teavm html4j / samples-gamepad shape.
