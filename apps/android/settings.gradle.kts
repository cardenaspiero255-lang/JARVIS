pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Official upstream Android AAR, isolated from normal Maven resolution.
        // Version and payload SHA-256 are independently gated in :app:preBuild.
        ivy {
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8")
            patternLayout { artifact("[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeGroup("com.k2fsa.sherpa.release") }
        }
    }
}
rootProject.name = "JarvisOmega"
include(":app")
