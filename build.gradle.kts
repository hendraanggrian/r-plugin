buildscript {
    repositories {
        jcenter()
        maven(REPO_GRADLE_PORTAL)
    }
    dependencies {
        classpath(kotlin("gradle-plugin", VERSION_KOTLIN))
        classpath(dokka())
        classpath(gitPublish())
        classpath(gradlePublish())
    }
}

allprojects {
    repositories {
        jcenter()
        maven(REPO_OSSRH_SNAPSHOTS)
    }
    tasks.withType<Delete> {
        delete(projectDir.resolve("out"))
    }
}