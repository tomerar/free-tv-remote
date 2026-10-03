package io.github.tomerar.freetvremote.protocol.remote

import io.github.tomerar.freetvremote.protocol.proto.RemoteAppLinkLaunchRequest
import io.github.tomerar.freetvremote.protocol.proto.RemoteConfigure
import io.github.tomerar.freetvremote.protocol.proto.RemoteDeviceInfo
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.proto.RemoteEditInfo
import io.github.tomerar.freetvremote.protocol.proto.RemoteImeBatchEdit
import io.github.tomerar.freetvremote.protocol.proto.RemoteImeObject
import io.github.tomerar.freetvremote.protocol.proto.RemoteKeyInject
import io.github.tomerar.freetvremote.protocol.proto.RemoteMessage
import io.github.tomerar.freetvremote.protocol.proto.RemotePingResponse
import io.github.tomerar.freetvremote.protocol.proto.RemoteSetActive

/** Builders for the messages the client sends. */
public object RemoteMessages {
    /** Feature bitmask announced to the TV: ping, key, IME, power, volume, app link. */
    public const val FEATURES: Int = 622

    public fun configure(config: RemoteSessionConfig): RemoteMessage =
        RemoteMessage(
            remote_configure =
                RemoteConfigure(
                    features = FEATURES,
                    device_info =
                        RemoteDeviceInfo(
                            model = config.deviceModel,
                            vendor = config.deviceVendor,
                            unknown1 = 1,
                            unknown2 = "1",
                            package_name = "io.github.tomerar.freetvremote",
                            app_version = config.appVersion,
                        ),
                ),
        )

    public fun setActive(): RemoteMessage = RemoteMessage(remote_set_active = RemoteSetActive(active = FEATURES))

    public fun pingResponse(value: Int): RemoteMessage =
        RemoteMessage(remote_ping_response = RemotePingResponse(val1 = value))

    public fun key(code: Int, direction: RemoteDirection): RemoteMessage =
        RemoteMessage(remote_key_inject = RemoteKeyInject(key_code = code, direction = direction))

    public fun appLink(link: String): RemoteMessage =
        RemoteMessage(remote_app_link_launch_request = RemoteAppLinkLaunchRequest(app_link = link))

    public fun text(text: String, imeCounter: Int, fieldCounter: Int): RemoteMessage =
        RemoteMessage(
            remote_ime_batch_edit =
                RemoteImeBatchEdit(
                    ime_counter = imeCounter,
                    field_counter = fieldCounter,
                    edit_info =
                        listOf(
                            RemoteEditInfo(
                                insert = 1,
                                text_field_status = RemoteImeObject(start = text.length - 1, end = text.length - 1, value_ = text),
                            ),
                        ),
                ),
        )
}
