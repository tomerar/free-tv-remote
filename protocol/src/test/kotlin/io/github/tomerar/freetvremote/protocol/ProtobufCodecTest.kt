package io.github.tomerar.freetvremote.protocol

import io.github.tomerar.freetvremote.protocol.proto.PairingMessage
import io.github.tomerar.freetvremote.protocol.proto.PairingRequest
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.proto.RemoteMessage
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.protocol.remote.RemoteMessages
import io.github.tomerar.freetvremote.protocol.remote.RemoteSessionConfig
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtobufCodecTest {
    private fun RemoteMessage.hex() =
        RemoteMessage.ADAPTER
            .encode(this)
            .toByteString()
            .hex()

    private fun PairingMessage.hex() =
        PairingMessage.ADAPTER
            .encode(this)
            .toByteString()
            .hex()

    @Test
    fun `short key press matches the known wire bytes`() {
        // field 10 (LEN) { key_code(1)=19, direction(2)=SHORT(3) }
        assertEquals("5204081310" + "03", RemoteMessages.key(KeyCodes.DPAD_UP, RemoteDirection.SHORT).hex())
    }

    @Test
    fun `ping response matches the known wire bytes`() {
        // field 9 (LEN) { val1(1)=5 }
        assertEquals("4a020805", RemoteMessages.pingResponse(5).hex())
    }

    @Test
    fun `set active announces the feature bitmask 622`() {
        // field 2 { active(1)=622 } ; 622 = 0xEE 0x04 as varint
        assertEquals("12 03 08 ee 04".replace(" ", ""), RemoteMessages.setActive().hex())
    }

    @Test
    fun `pairing request matches the known wire bytes`() {
        val message =
            PairingMessage(
                protocol_version = 2,
                status = PairingMessage.Status.STATUS_OK,
                pairing_request = PairingRequest(service_name = "atvremote", client_name = "x"),
            )
        // version=2, status=200 (c8 01), field 10 { 1:"atvremote", 2:"x" }
        assertEquals("0802" + "10c801" + "52" + "0e" + "0a09" + "6174767265" + "6d6f7465" + "1201" + "78", message.hex())
    }

    @Test
    fun `remote messages survive a round trip`() {
        val original = RemoteMessages.configure(RemoteSessionConfig(deviceModel = "Phone", deviceVendor = "Me"))
        val decoded = RemoteMessage.ADAPTER.decode(RemoteMessage.ADAPTER.encode(original))
        assertEquals(original, decoded)
        assertEquals("Phone", decoded.remote_configure?.device_info?.model)
        assertEquals(622, decoded.remote_configure?.features)
    }

    @Test
    fun `text edit carries counters and the typed value`() {
        val decoded = RemoteMessage.ADAPTER.decode(RemoteMessage.ADAPTER.encode(RemoteMessages.text("héllo", 4, 9)))
        val edit = decoded.remote_ime_batch_edit!!
        assertEquals(4, edit.ime_counter)
        assertEquals(9, edit.field_counter)
        assertEquals(
            "héllo",
            edit.edit_info
                .single()
                .text_field_status
                ?.value_,
        )
    }

    @Test
    fun `unknown fields from newer TVs are ignored`() {
        // field 99 (LEN, 2 bytes) followed by a valid ping request in field 8
        val bytes = "9a0602aabb".decodeHex().toByteArray() + "42020805".decodeHex().toByteArray()
        assertEquals(
            5,
            RemoteMessage.ADAPTER
                .decode(bytes)
                .remote_ping_request
                ?.val1,
        )
    }

    @Test
    fun `app link request round trips`() {
        val decoded = RemoteMessage.ADAPTER.decode(RemoteMessage.ADAPTER.encode(RemoteMessages.appLink("https://www.netflix.com/")))
        assertEquals("https://www.netflix.com/", decoded.remote_app_link_launch_request?.app_link)
    }
}
