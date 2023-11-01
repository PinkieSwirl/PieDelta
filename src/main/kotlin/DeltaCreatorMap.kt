package eu.pieland.delta

public interface DeltaCreatorMap {

    public fun getOrDefault(key: Long, default: Int): Int

    public fun putIfAbsent(key: Long, value: Int)
}

internal class HashDeltaCreatorMap : DeltaCreatorMap {
    private val map: MutableMap<Long, Int> = HashMap()

    override fun getOrDefault(key: Long, default: Int): Int = map.getOrDefault(key, default)

    override fun putIfAbsent(key: Long, value: Int) {
        map.putIfAbsent(key, value)
    }
}
