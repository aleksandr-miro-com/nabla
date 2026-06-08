package com.miro.nabla

internal object OperationUtils {
    fun checkRange(beginIndex: Int, endIndex: Int, length: Int) {
        if (beginIndex !in 0..<length) {
            throw IllegalArgumentException("Index $beginIndex out of range: [0; ${length - 1}]")
        }
        if (endIndex !in 1..length) {
            throw IllegalArgumentException("Index $endIndex out of range: [1; $length]")
        }
        val subLen = endIndex - beginIndex
        if (subLen <= 0) {
            throw IllegalArgumentException("Index $beginIndex out of range: [0; ${endIndex - 1}]")
        }
    }
}