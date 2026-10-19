package com.cardenaspiero255.jarvis.core

/** Small pure reference policy used by the JVM unit tests.
 * Native storage follows the same identity contract under inference_mutex.
 * An unverified revision MUST NEVER be a cache key.
 */
enum class CacheAction { REJECT, LOAD, REUSE, REPLACE }

class NativeModelCacheState {
    private var path: String? = null
    private var sha256: String? = null

    @Synchronized fun classify(nextPath: String, revision: String): CacheAction {
        if (nextPath.isBlank() || !revision.matches(Regex("[0-9a-f]{64}"))) {
            return CacheAction.REJECT
        }
        val decision = when {
            sha256 == null -> CacheAction.LOAD
            sha256 == revision && path == nextPath -> CacheAction.REUSE
            else -> CacheAction.REPLACE
        }
        path = nextPath
        sha256 = revision
        return decision
    }

    @Synchronized fun invalidate() {
        sha256 = null
        path = null
    }
}
