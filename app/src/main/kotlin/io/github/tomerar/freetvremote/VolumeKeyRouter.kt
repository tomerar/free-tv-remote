package io.github.tomerar.freetvremote

/**
 * Tells the Activity whether the phone's hardware volume buttons should drive the TV
 * (only while the remote screen is showing and the setting is on).
 */
class VolumeKeyRouter {
    @Volatile
    var active: Boolean = false
}
