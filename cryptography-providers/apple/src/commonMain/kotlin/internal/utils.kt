/*
 * Copyright (c) 2023-2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.cryptography.providers.apple.internal

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*

private val EmptyNSData = NSData()
internal val EmptyByteArray = ByteArray(0)

private val almostEmptyArray = ByteArray(1)

private val almostEmptyArrayPinned = ByteArray(1).pin()

internal fun Pinned<ByteArray>.safeAddressOf(index: Int): CPointer<ByteVar> {
    if (index == get().size) return almostEmptyArrayPinned.addressOf(0)
    return addressOf(index)
}

//this hack will be dropped with introducing of new IO or functions APIs
internal fun ByteArray.fixEmpty(): ByteArray = if (isNotEmpty()) this else almostEmptyArray

internal fun ByteArray.refToFixed(index: Int): CValuesRef<ByteVar> {
    if (index == size) return almostEmptyArray.refTo(0)
    return refTo(index)
}

internal val CFErrorRefVar.releaseAndGetMessage get() = value.releaseBridgeAs<NSError>()?.description

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Any?.retainBridgeAs(): T? = retainBridge()?.let { it as T }
internal fun Any?.retainBridge(): CFTypeRef? = CFBridgingRetain(this)

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> CFTypeRef?.releaseBridgeAs(): T? = releaseBridge()?.let { it as T }
internal fun CFTypeRef?.releaseBridge(): Any? = CFBridgingRelease(this)

internal fun CFTypeRef?.release(): Unit = CFRelease(this)

internal inline fun <T : CFTypeRef?, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        releaseBridge()
    }
}

internal fun CFMutableDictionaryRef?.add(key: CFTypeRef?, value: CFTypeRef?) {
    CFDictionaryAddValue(this, key, value)
}

@Suppress("FunctionName")
@OptIn(UnsafeNumber::class)
internal inline fun CFMutableDictionary(size: Int, block: CFMutableDictionaryRef?.() -> Unit): CFMutableDictionaryRef? {
    val dict = CFDictionaryCreateMutable(null, size.convert(), null, null)
    dict.block()
    return dict
}

@OptIn(UnsafeNumber::class)
internal fun NSData.toByteArray(): ByteArray {
    if (length.convert<Int>() == 0) return EmptyByteArray

    return ByteArray(length.convert()).apply {
        usePinned {
            getBytes(it.addressOf(0), length)
        }
    }
}

@OptIn(UnsafeNumber::class)
internal fun <R> ByteArray.useNSData(block: (NSData) -> R): R {
    if (isEmpty()) return block(EmptyNSData)

    return usePinned {
        block(
            NSData.dataWithBytesNoCopy(
                bytes = it.addressOf(0),
                length = size.convert(),
                freeWhenDone = true
            )
        )
    }
}

internal fun checkBounds(size: Int, startIndex: Int, endIndex: Int) {
    if (startIndex < 0 || endIndex > size) {
        throw IndexOutOfBoundsException(
            "startIndex ($startIndex) and endIndex ($endIndex) are not within the range [0..size($size))"
        )
    }
    if (startIndex > endIndex) {
        throw IllegalArgumentException("startIndex ($startIndex) > endIndex ($endIndex)")
    }
}
