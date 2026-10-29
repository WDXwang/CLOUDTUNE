// CI 环境（如 GitHub Actions，CI=true）走官方源；本地（中国大陆）默认走阿里云镜像。
val isCI = System.getenv("CI") != null

pluginManagement {
    repositories {
        if (isCI) {
            google()
            gradlePluginPortal()
            mavenCentral()
        } else {
            // 国内镜像优先，便于在中国大陆网络环境下编译
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (isCI) {
            google()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "CloudTune"
include(":app")
