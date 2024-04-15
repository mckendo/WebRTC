package com.cykendo.myapplication

import org.webrtc.*

open class MySdpObserver(name : String) : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription) {
    }

    override fun onSetSuccess() {
    }

    override fun onCreateFailure(p0: String) {
    }

    override fun onSetFailure(p0: String) {
    }
}