package com.nuvio.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ry fork: the app is split into two halves, switched from the first button of the tab bar.
 *
 * - [Mode.NUVIO]: Home, Search (your addons' catalogs, as in Nuvio), Library.
 * - [Mode.IPTV]: Live TV (with On Demand, Recordings and the guide), Sports, and its own Search
 *   over channels, programmes and On Demand.
 *
 * The mode follows the tab you're on: Home or Library make it Nuvio, Live TV or Sports make it
 * IPTV. Search and Settings belong to both and keep the mode you were in.
 */
object AppMode {
    enum class Mode { NUVIO, IPTV }

    private val _mode = MutableStateFlow(Mode.NUVIO)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    fun set(mode: Mode) {
        _mode.value = mode
    }

    fun modeFor(tab: AppScreenTab): Mode? = when (tab) {
        AppScreenTab.Home, AppScreenTab.Library -> Mode.NUVIO
        AppScreenTab.LiveTv, AppScreenTab.Sports -> Mode.IPTV
        AppScreenTab.Search, AppScreenTab.Settings -> null
    }

    fun follow(tab: AppScreenTab) {
        modeFor(tab)?.let { _mode.value = it }
    }
}

/** Ry fork: how strictly the two halves are kept apart. */
object AppModeSplit {
    /** Home (Nuvio's) shows no Live TV, Sports or On Demand rows. */
    const val HOME_KEEPS_IPTV_ROWS = false
}
