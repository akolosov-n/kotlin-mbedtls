/*
 * Copyright (c) 2022 kotlin-mbedtls contributors (https://github.com/open-coap/kotlin-mbedtls)
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencoap.ssl.transport

import org.opencoap.ssl.SslConfig
import org.opencoap.ssl.SslContext
import org.opencoap.ssl.SslHandshakeContext
import org.opencoap.ssl.SslSession
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/*
DTLS transmitter based on DatagramChannel. Uses blocking calls.
 */
class DtlsTransmitter private constructor(
    internal val channel: DatagramChannel,
    private val sslSession: SslSession,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) {
    companion object {
        fun connect(server: DtlsServer, conf: SslConfig): CompletableFuture<DtlsTransmitter> {
            return connect(InetSocketAddress(InetAddress.getLocalHost(), server.localPort()), conf)
        }

        fun connect(dest: InetSocketAddress, conf: SslConfig, bindPort: Int = 0): CompletableFuture<DtlsTransmitter> {
            val channel: DatagramChannel = DatagramChannel.open()
                .bind(InetSocketAddress("0.0.0.0", bindPort))
                .connect(dest)

            return connect(dest, conf, channel)
        }

        fun connect(dest: InetSocketAddress, conf: SslConfig, channel: DatagramChannel): CompletableFuture<DtlsTransmitter> {
            val executor = Executors.newSingleThreadExecutor()
            return executor.supply {
                val sslSession = handshake(conf.newContext(), channel, dest)
                DtlsTransmitter(channel, sslSession, executor)
            }
        }

        private fun handshake(handshakeCtx: SslHandshakeContext, channel: DatagramChannel, dest: InetSocketAddress): SslSession {
            val send: (ByteBuffer) -> Unit = { channel.send(it, dest) }

            val buffer: ByteBuffer = ByteBuffer.allocateDirect(16384)
            buffer.clear().flip()
            var sslContext: SslContext = handshakeCtx.step(buffer, send)

            while (sslContext is SslHandshakeContext) {
                buffer.clear()
                channel.receive(buffer)
                buffer.flip()
                sslContext = handshakeCtx.step(buffer, send)
            }
            return sslContext as SslSession
        }

        fun create(dest: InetSocketAddress, sslSession: SslSession, bindPort: Int = 0): DtlsTransmitter {
            val channel: DatagramChannel = DatagramChannel.open()
                .bind(InetSocketAddress("0.0.0.0", bindPort))
                .connect(dest)

            return DtlsTransmitter(channel, sslSession, Executors.newSingleThreadExecutor())
        }
    }

    fun close() {
        channel.close()
    }

    fun send(data: ByteArray) {
        executor.supply { channel.write(sslSession.encrypt(data)) }.join()
    }

    fun send(text: String) = send(text.encodeToByteArray())

    fun receive(): ByteArray {
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(16384)
        buffer.clear()
        channel.receive(buffer)
        buffer.flip()

        return executor.supply { sslSession.decrypt(buffer) }.join()
    }

    fun receiveString(): String = receive().decodeToString()

    fun getCipherSuite() = sslSession.getCipherSuite()
    fun getPeerCid() = sslSession.getPeerCid()
    fun saveSession() = sslSession.save()
}
