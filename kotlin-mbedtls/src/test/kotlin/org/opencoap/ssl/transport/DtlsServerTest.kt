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

import org.awaitility.kotlin.await
import org.junit.After
import org.opencoap.ssl.SslConfig
import org.opencoap.ssl.SslException
import org.opencoap.ssl.util.toByteBuffer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DtlsServerTest {

    private val psk = Pair("dupa".encodeToByteArray(), byteArrayOf(1))
    private val conf: SslConfig = SslConfig.server(psk.first, psk.second)
    private val clientConfig = SslConfig.client(psk.first, psk.second)
    private lateinit var server: DtlsServer
    private val echoHandler: (InetSocketAddress, ByteArray) -> Unit = { adr: InetSocketAddress, packet: ByteArray ->
        if (packet.decodeToString() == "error") {
            throw Exception("error")
        } else {
            server.send(packet.plus(":resp".encodeToByteArray()), adr)
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun testSingleConnection() {
        server = DtlsServer.create(conf).listen(echoHandler)

        val client = DtlsTransmitter.connect(server, clientConfig).join()

        client.send("perse")
        assertEquals("perse:resp", client.receiveString())

        assertEquals(1, server.numberOfSessions())
        client.close()
    }

    @Test
    fun testMultipleConnections() {
        server = DtlsServer.create(conf).listen(echoHandler)

        val clients: List<DtlsTransmitter> = (1..10).map {
            val client = DtlsTransmitter.connect(server, clientConfig).join()

            client.send("dupa$it")
            assertEquals("dupa$it:resp", client.receiveString())

            client
        }

        assertEquals(10, server.numberOfSessions())
        clients.forEach(DtlsTransmitter::close)
    }

    @Test
    fun testFailedHandshake() {
        // given
        server = DtlsServer.create(conf).listen(echoHandler)
        val clientFut = DtlsTransmitter.connect(server, SslConfig.client(psk.first, byteArrayOf(-128)))

        // when
        assertTrue(runCatching { clientFut.join() }.exceptionOrNull()?.cause is SslException)

        // then
        await.untilAsserted {
            assertEquals(0, server.numberOfSessions())
        }
    }

    @Test
    fun testReceiveMalformedPacket() {
        // given
        server = DtlsServer.create(conf).listen(echoHandler)
        val client = DtlsTransmitter.connect(server, clientConfig).join()
        client.send("perse")

        // when
        client.channel.write("malformed dtls packet".toByteBuffer())

        // then
        await.untilAsserted {
            assertEquals(0, server.numberOfSessions())
        }
        client.close()
    }

    @Test
    fun shouldCatchExceptionFromHandler() {
        server = DtlsServer.create(conf).listen(echoHandler)
        val client = DtlsTransmitter.connect(server, clientConfig).join()

        // when
        client.send("error")
        client.send("perse")

        // then
        assertEquals("perse:resp", client.receiveString())

        assertEquals(1, server.numberOfSessions())
        client.close()
    }
}
