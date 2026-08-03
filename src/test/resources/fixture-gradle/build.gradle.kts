allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = "https://nexus.internal.example.com/repo" }
        // maven { url = "https://never.example.com/commented-out" }
    }
}

// Project metadata, not repositories. teavm has exactly this, and matching `url =` anywhere
// reported its homepage and the Apache licence text as repositories consumers would need.
publishing {
    publications {
        create<MavenPublication>("main") {
            pom {
                url = "https://not-a-repository.example.org"
                licenses {
                    license {
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                scm {
                    url = "https://github.com/example/fixture-gradle"
                }
            }
        }
    }
}
