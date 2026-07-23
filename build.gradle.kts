plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "dev.unipost"
version = "0.6.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.19.0")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        val releasesRepo = System.getenv("MAVEN_CENTRAL_DEPLOY_URL")
            ?: findProperty("mavenCentralDeployUrl") as String?
        if (!releasesRepo.isNullOrBlank()) {
            maven {
                name = "mavenCentral"
                url = uri(releasesRepo)
                credentials {
                    username = System.getenv("MAVEN_CENTRAL_USERNAME")
                        ?: findProperty("mavenCentralUsername") as String?
                    password = System.getenv("MAVEN_CENTRAL_PASSWORD")
                        ?: findProperty("mavenCentralPassword") as String?
                }
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "sdk-java"
            version = project.version.toString()

            pom {
                name.set("UniPost Java SDK")
                description.set("Official UniPost API client for Java")
                url.set("https://unipost.dev/docs")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        name.set("UniPost")
                        email.set("hello@unipost.dev")
                    }
                }
                scm {
                    url.set("https://github.com/unipost-dev/sdk-java")
                    connection.set("scm:git:https://github.com/unipost-dev/sdk-java.git")
                    developerConnection.set("scm:git:ssh://git@github.com/unipost-dev/sdk-java.git")
                }
            }
        }
    }
}

signing {
    val signingKey = System.getenv("ORG_GRADLE_PROJECT_signingKey")
        ?: findProperty("signingKey") as String?
    val signingPassword = System.getenv("ORG_GRADLE_PROJECT_signingPassword")
        ?: findProperty("signingPassword") as String?

    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
