/*
 * Copyright (c) 2023-2024 Oleg Yukhnevich. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.whyoleg.cryptography.providers.tests.algorithms.symmetric

import dev.whyoleg.cryptography.*
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.algorithms.symmetric.*
import dev.whyoleg.cryptography.providers.tests.api.compatibility.*
import dev.whyoleg.cryptography.random.*
import kotlinx.serialization.*
import kotlin.test.*

private const val maxPlaintextSize = 10000
private const val maxAssociatedDataSize = 10000

abstract class AesGcmCompatibilityTest(provider: CryptographyProvider) :
    AesBasedCompatibilityTest<AES.GCM.Key, AES.GCM>(AES.GCM, provider) {

    @Serializable
    private data class CipherParameters(val tagSizeBits: Int) : TestParameters

    override suspend fun CompatibilityTestScope<AES.GCM>.generate(isStressTest: Boolean) {
        val associatedDataIterations = when {
            isStressTest -> 10
            else         -> 5
        }
        val cipherIterations = when {
            isStressTest -> 10
            else         -> 5
        }


        val tagSizes = listOf(96, 128).map { tagSizeBits ->
            val id = api.ciphers.saveParameters(CipherParameters(tagSizeBits))
            id to tagSizeBits.bits
        }

        generateKeys(isStressTest) { key, keyReference, _ ->
            tagSizes.forEach { (cipherParametersId, tagSize) ->
                logger.log { "tagSize = $tagSize" }
                val cipher = key.cipher(tagSize)
                repeat(associatedDataIterations) { adIndex ->
                    val associatedDataSize = if (adIndex == 0) null else CryptographyRandom.nextInt(maxAssociatedDataSize)
                    logger.log { "associatedData.size = $associatedDataSize" }
                    val associatedData = associatedDataSize?.let(CryptographyRandom::nextBytes)
                    repeat(cipherIterations) {
                        val plaintextSize = CryptographyRandom.nextInt(maxPlaintextSize)
                        logger.log { "plaintext.size      = $plaintextSize" }
                        val plaintext = CryptographyRandom.nextBytes(plaintextSize)
                        val ciphertext = cipher.encrypt(plaintext, associatedData)
                        logger.log { "ciphertext.size     = ${ciphertext.size}" }

                        assertContentEquals(plaintext, cipher.decrypt(ciphertext, associatedData), "Initial Decrypt")

                        api.ciphers.saveData(
                            cipherParametersId,
                            AuthenticatedCipherData(keyReference, associatedData, plaintext, ciphertext)
                        )
                    }
                }
            }
        }
    }

    override suspend fun CompatibilityTestScope<AES.GCM>.validate() {
        val keys = validateKeys()

        api.ciphers.getParameters<CipherParameters> { (tagSize), parametersId, _ ->
            api.ciphers.getData<AuthenticatedCipherData>(parametersId) { (keyReference, associatedData, plaintext, ciphertext), _, _ ->
                keys[keyReference]?.forEach { key ->
                    val cipher = key.cipher(tagSize.bits)
                    assertContentEquals(plaintext, cipher.decrypt(ciphertext, associatedData), "Decrypt")
                    assertContentEquals(
                        plaintext,
                        cipher.decrypt(cipher.encrypt(plaintext, associatedData), associatedData),
                        "Encrypt-Decrypt"
                    )
                }
            }
        }
    }
}
