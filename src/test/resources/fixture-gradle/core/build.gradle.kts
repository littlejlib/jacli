plugins {
    `java-library`
}

dependencies {
    implementation(libs.asm.commons)
    implementation("com.carrotsearch:hppc:0.10.0")
    compileOnly(libs.split.table)
    testImplementation(libs.junit)
}

// a url in a string literal must survive comment stripping: https://example.com/x
