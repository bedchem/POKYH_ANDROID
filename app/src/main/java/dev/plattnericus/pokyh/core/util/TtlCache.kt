package dev.plattnericus.pokyh.core.util

/**
 * Generischer In-Memory-Cache mit Ablaufzeit (TTL) — Android-Pendant zu iOS' `TTLCache`
 * (Cache.swift). Eine zentrale, saubere Lösung statt verstreuter Maps in jedem Client.
 *
 * Thread-safe via `synchronized` auf dem internen Store (mehrere Repositories/ViewModels
 * können gleichzeitig aus verschiedenen Coroutine-Dispatchern zugreifen).
 */
class TtlCache<K, V>(private val ttlMillis: Long) {
    private data class Entry<V>(val value: V, val time: Long)

    private val store = HashMap<K, Entry<V>>()
    private val lock = Any()

    /** Gültiger Wert oder null (fehlend/abgelaufen). Abgelaufene Einträge werden entfernt. */
    fun get(key: K): V? = synchronized(lock) {
        val entry = store[key] ?: return null
        if (System.currentTimeMillis() - entry.time >= ttlMillis) {
            store.remove(key)
            return null
        }
        entry.value
    }

    fun set(key: K, value: V) {
        synchronized(lock) { store[key] = Entry(value, System.currentTimeMillis()) }
    }

    /** Auch abgelaufene Werte (z. B. als Stale-Fallback bei Netzwerkfehler). Null, falls nie gesetzt. */
    fun stale(key: K): V? = synchronized(lock) { store[key]?.value }

    fun remove(key: K) {
        synchronized(lock) { store.remove(key) }
    }

    fun removeAll() {
        synchronized(lock) { store.clear() }
    }
}
