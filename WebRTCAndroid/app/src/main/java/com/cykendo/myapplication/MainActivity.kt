package com.cykendo.myapplication

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.cykendo.myapplication.databinding.ActivityMainBinding
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.*
import java.net.URISyntaxException
import java.util.UUID


class MainActivity : AppCompatActivity() {

    private val TAG = "WebRTCTest"

    private lateinit var binding: ActivityMainBinding

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var localVideoView: SurfaceViewRenderer
    private lateinit var remoteVideoView: SurfaceViewRenderer
    private lateinit var localVideoTrack: VideoTrack

    private lateinit var socket: Socket

    private val SERVER_URL = "http://192.168.219.112:3000"
//    private val SERVER_URL = "http://192.168.219.107:3000"
    //private val SERVER_URL = "https://b3aa-116-37-5-142.ngrok-free.app"


    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)

        requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 1)

        initializeWebRTC()
        connectSocket()
    }

    private fun initializeWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val eglBaseContext = EglBase.create().eglBaseContext

        val hardwareEncoder = HardwareVideoEncoderFactory(eglBaseContext, true, true)
        val videoEncoderFactory =
            SimulcastVideoEncoderFactory(hardwareEncoder, SoftwareVideoEncoderFactory())

        val factoryOptions = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setVideoEncoderFactory(videoEncoderFactory)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
//            this.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
//            this.bundlePolicy = PeerConnection.BundlePolicy.BALANCED;
//            this.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
//            this.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
//            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
//            this.enableDscp = true
//            this.keyType = PeerConnection.KeyType.ECDSA;
        }

        // PeerConnection 생성
        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {
                    Log.d(TAG, "peerConnection-onSignalingChange")
                }

                override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "peerConnection-onIceConnectionChange $iceConnectionState")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.d(TAG, "peerConnection-onIceConnectionReceivingChange")
                }

                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "peerConnection-onIceGatheringChange")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate?) {
                    Log.d(TAG, "peerConnection-onIceCandidate")
                    iceCandidate?.let {
                        val jsonObject = JSONObject().apply {
                            put("sdpMid", it.sdpMid)
                            put("sdpMLineIndex", it.sdpMLineIndex)
                            put("candidate", it.sdp)
                        }

                        socket.emit("ice", jsonObject, "1")

                        Log.d(TAG, "SEND ICE $jsonObject")
                    }
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                    Log.d(TAG, "peerConnection-onIceCandidatesRemoved")
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    Log.d(TAG, "peerConnection-onAddStream $mediaStream")

                    runOnUiThread {
                        mediaStream.videoTracks.firstOrNull()?.addSink(remoteVideoView)
                    }
                }

                override fun onRemoveStream(p0: MediaStream?) {
                    Log.d(TAG, "peerConnection-onRemoveStream $p0")
                }

                override fun onDataChannel(p0: DataChannel?) {
                    Log.d(TAG, "peerConnection-onDataChannel")
                }

                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "peerConnection-onRenegotiationNeeded")
                }
            }
        ) ?: return

        localVideoView.init(eglBaseContext, null)
        localVideoView.setEnableHardwareScaler(true)
        localVideoView.setMirror(true)

        remoteVideoView.init(eglBaseContext, null)
        remoteVideoView.setEnableHardwareScaler(true)
        remoteVideoView.setMirror(true)

        val videoSource = peerConnectionFactory.createVideoSource(false)
        val videoCapturer = createCameraCapturer(Camera1Enumerator(false))
        videoCapturer?.initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBaseContext),
            this,
            videoSource.capturerObserver
        )
        videoCapturer?.startCapture(1280, 1280, 30)
        localVideoTrack =
            peerConnectionFactory.createVideoTrack("Video${UUID.randomUUID()}", videoSource).apply {
                addSink(localVideoView)
            }

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val audioTrack =
            peerConnectionFactory.createAudioTrack("Audio${UUID.randomUUID()}", audioSource)

        peerConnection.addTrack(localVideoTrack)
        peerConnection.addTrack(audioTrack)

    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    private fun connectSocket() {
        try {
            socket = IO.socket(SERVER_URL)
            setupSocketEvents()
            socket.connect()
            Log.d(TAG, "connect")
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            Log.d(TAG, "error!!")
        }
    }

    private fun setupSocketEvents() {
        socket.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "EVENT_CONNECT")
            socket.emit("join_room", "1")
        }.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "Disconnected from server")
        }.on("welcome") {
            Log.d(TAG, "welcome~ OFFER")

            val myDataChannel = peerConnection.createDataChannel("chat", DataChannel.Init())
            myDataChannel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(p0: Long) {}
                override fun onStateChange() {}

                override fun onMessage(buffer: DataChannel.Buffer) {
                    val data = String(buffer.data.array())
                    Log.d(TAG, data)
                }
            })

            peerConnection.createOffer(object : MySdpObserver("offer") {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    peerConnection.setLocalDescription(
                        MySdpObserver("local"),
                        sessionDescription
                    )
                    // a=msid:<stream id> <track id>
                    // unspecified stream id '-' remove !!!!!!!!!!!!!!
                    val sdp = sessionDescription.description.replace("a=msid:- ", "a=msid:")

                    val jsonObject = JSONObject().apply {
                        put("type", "offer")
                        put("sdp", sdp)
                    }
                    socket.emit("offer", jsonObject, "1")
                    Log.d(TAG, "SEND Offer!!! $jsonObject")
                }
            }, MediaConstraints())
        }.on("offer") { args ->
            val offerJson = args[0] as JSONObject
            val sdp = offerJson.get("sdp") as String
            Log.d(TAG, "RECV offer $offerJson")

            peerConnection.setRemoteDescription(MySdpObserver("remote"), SessionDescription(SessionDescription.Type.OFFER, sdp))

            peerConnection.createAnswer(object : MySdpObserver("answer") {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    peerConnection.setLocalDescription(MySdpObserver("local"), sessionDescription)
                    val answerSdp = sessionDescription.description.replace("a=msid:- ", "a=msid:")
                    val jsonObject = JSONObject().apply {
                        put("type", "answer")
                        put("sdp", answerSdp)
                    }
                    socket.emit("answer", jsonObject, "1")
                    Log.d(TAG, "SEND Answer!!! $jsonObject")
                }
            }, MediaConstraints())
        }.on("answer") { args ->
            val answerJson = args[0] as JSONObject
            Log.d(TAG, "RECV answer $answerJson")
            val sdp = answerJson.get("sdp") as String
            peerConnection.setRemoteDescription(MySdpObserver("remote"), SessionDescription(SessionDescription.Type.ANSWER, sdp))
        }.on("ice") { args ->
            Log.d(TAG, "RECV ICE " + args[0])

            if (args[0] != null) {
                val iceJson = args[0] as JSONObject
                val iceCandidate = IceCandidate(
                    iceJson.get("sdpMid") as String,
                    iceJson.get("sdpMLineIndex") as Int,
                    iceJson.get("candidate") as String
                )
                peerConnection.addIceCandidate(iceCandidate)
            }
        }
    }
}