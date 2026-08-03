plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(libs.grouped)
}
