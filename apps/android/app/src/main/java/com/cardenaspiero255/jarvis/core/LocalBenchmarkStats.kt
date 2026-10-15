package com.cardenaspiero255.jarvis.core

/** Presentation-only statistics, never fabricates missing device samples. */
object LocalBenchmarkStats {
    fun medianPositive(samples: List<Float>): Float? {
        val valid = samples.filter { it.isFinite() && it > 0f }.sorted()
        if (valid.isEmpty()) return null
        val mid = valid.size / 2
        return if (valid.size % 2 != 0) valid[mid] else
            (valid[mid - 1].toDouble() + valid[mid].toDouble()).div(2.0).toFloat()
    }
}
