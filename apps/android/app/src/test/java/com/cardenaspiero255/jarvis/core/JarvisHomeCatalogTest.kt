package com.cardenaspiero255.jarvis.core

import org.junit.Assert.*
import org.junit.Test

/** Contract for a user-selectable HOME surface; no privileged system access. */
class JarvisHomeCatalogTest {
    private val catalog = listOf(
        JarvisHomeApp("com.example.maps", "com.example.maps.Main", "Mapas"),
        JarvisHomeApp("com.example.camera", "com.example.camera.Main", "Cámara"),
        JarvisHomeApp("com.example.music", "com.example.music.Main", "Música"),
        JarvisHomeApp("com.example.camera", "com.example.camera.Main", "Cámara duplicada"),
        JarvisHomeApp("com.cardenaspiero255.jarvis", "com.cardenaspiero255.jarvis.MainActivity", "JARVIS OMEGA")
    )

    @Test fun sortedWithoutDuplicatesOrSelfLauncher() {
        val result = JarvisHomeCatalog.visible(catalog, "", "com.cardenaspiero255.jarvis")
        assertEquals(listOf("Cámara", "Mapas", "Música"), result.map { it.label })
        assertEquals(3, result.size)
    }

    @Test fun searchesByAccentCaseOrPackageWithoutChangingInstalledApps() {
        val original = catalog.toList()
        assertEquals(listOf("Cámara"), JarvisHomeCatalog.visible(catalog, "CAMARA", "com.cardenaspiero255.jarvis").map { it.label })
        assertEquals(listOf("Música"), JarvisHomeCatalog.visible(catalog, "mus", "com.cardenaspiero255.jarvis").map { it.label })
        assertEquals(listOf("Mapas"), JarvisHomeCatalog.visible(catalog, "example.maps", "com.cardenaspiero255.jarvis").map { it.label })
        assertEquals(original, catalog)
    }

    @Test fun invalidComponentsNeverBecomeLaunchTargets() {
        val invalid = listOf(
            JarvisHomeApp("", "Main", "Sin paquete"),
            JarvisHomeApp("com.example.ok", "", "Sin actividad"),
            JarvisHomeApp("com.example.ok", "com.example.ok.Main", "  "),
            JarvisHomeApp("com.example.ok", "com.example.ok.Main", "Correcto")
        )
        assertEquals(listOf("Correcto"), JarvisHomeCatalog.visible(invalid, "", "com.cardenaspiero255.jarvis").map { it.label })
    }

    @Test fun limitOnlyCapsRenderingNotSearchOfEntireCollection() {
        val apps = (0 until 100).map {
            JarvisHomeApp("p$it", "p$it.Main", "Aplicación $it")
        }
        assertEquals(48, JarvisHomeCatalog.visible(apps, "", "self", limit = 48).size)
        assertEquals(listOf("Aplicación 99"), JarvisHomeCatalog.visible(apps, "99", "self", limit = 48).map { it.label })
    }
}
