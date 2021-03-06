package io.mrlokop.kotlin.utils.conventer.enities

open class PrimitiveEntity(val data: String) : AbstractEntity("primitive-entity")

class IntPrimitiveEntity(data: String) : PrimitiveEntity(data) {
    fun get() : Int {
        return data.toInt()
    }
}