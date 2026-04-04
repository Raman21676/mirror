package com.mirror.target.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SdpObserver
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class WebRtcServerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302"
        )
        private const val DATA_CHANNEL_LABEL = "mirror-data"
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var eglBase: EglBase? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + scope.coroutineContext)
    private var pendingIceCandidates = ConcurrentLinkedQueue<IceCandidate>()
    private val isInitialized = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)
    
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onSignalingData: ((String) -> Unit)? = null
    var onDataReceived: ((ByteArray) -> Unit)? = null

    fun initialize() {
        if (isInitialized.get()) return
        ioScope.launch {
            try {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
                )
                eglBase = EglBase.create()
                val encoderFactory = DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true)
                val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory()
                isInitialized.set(true)
                Timber.i("WebRTC initialized")
            } catch (e: Exception) {
                Timber.e(e, "WebRTC init failed")
            }
        }
    }

    fun createAnswer(remoteOfferSdp: String) {
        if (!isInitialized.get()) return
        ioScope.launch {
            try {
                val iceServers = STUN_SERVERS.map { PeerConnection.IceServer.builder(it).createIceServer() }
                val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                    tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                    rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                }
                
                peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                        when (state) {
                            PeerConnection.IceConnectionState.CONNECTED,
                            PeerConnection.IceConnectionState.COMPLETED -> {
                                isConnected.set(true)
                                onConnectionStateChanged?.invoke(true)
                            }
                            PeerConnection.IceConnectionState.DISCONNECTED,
                            PeerConnection.IceConnectionState.FAILED -> {
                                isConnected.set(false)
                                onConnectionStateChanged?.invoke(false)
                            }
                            else -> {}
                        }
                    }
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                    override fun onIceCandidate(candidate: IceCandidate?) {
                        candidate?.let {
                            val data = IceCandidateData(it.sdpMid, it.sdpMLineIndex, it.sdp)
                            onSignalingData?.invoke(data.toJson())
                        }
                    }
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                    override fun onAddStream(stream: org.webrtc.MediaStream?) {}
                    override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
                    override fun onDataChannel(dc: DataChannel?) {
                        dataChannel = dc
                        dataChannel?.registerObserver(object : DataChannel.Observer {
                            override fun onBufferedAmountChange(previousAmount: Long) {}
                            override fun onStateChange() {}
                            override fun onMessage(buffer: DataChannel.Buffer?) {
                                buffer?.data?.let { bb ->
                                    val data = ByteArray(bb.remaining())
                                    bb.get(data)
                                    onDataReceived?.invoke(data)
                                }
                            }
                        })
                    }
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
                })
                
                val dcInit = DataChannel.Init().apply { ordered = false; maxRetransmits = 0 }
                dataChannel = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, dcInit)
                dataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    override fun onStateChange() {}
                    override fun onMessage(buffer: DataChannel.Buffer?) {
                        buffer?.data?.let { bb ->
                            val data = ByteArray(bb.remaining())
                            bb.get(data)
                            onDataReceived?.invoke(data)
                        }
                    }
                })
                
                val offer = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() { createAnswerInternal() }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {}
                }, offer)
            } catch (e: Exception) {
                Timber.e(e, "Error creating answer")
            }
        }
    }

    private fun createAnswerInternal() {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        sdp?.description?.let {
                            onSignalingData?.invoke(SignalingData("answer", it).toJson())
                        }
                        sendPendingIceCandidates()
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    fun addIceCandidate(json: String) {
        try {
            val c = IceCandidateData.fromJson(json)
            val ice = IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)
            if (peerConnection?.remoteDescription != null) {
                peerConnection?.addIceCandidate(ice)
            } else {
                pendingIceCandidates.offer(ice)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to add ICE candidate")
        }
    }

    private fun sendPendingIceCandidates() {
        while (pendingIceCandidates.isNotEmpty()) {
            pendingIceCandidates.poll()?.let { peerConnection?.addIceCandidate(it) }
        }
    }

    fun sendBinary(data: ByteArray): Boolean {
        if (!isConnected.get()) return false
        return try {
            dataChannel?.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(data), true)) ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun isConnected(): Boolean = isConnected.get()

    fun disconnect() {
        isConnected.set(false)
        dataChannel?.close()
        peerConnection?.close()
    }

    fun dispose() {
        isConnected.set(false)
        dataChannel?.close()
        peerConnection?.close()
        peerConnectionFactory?.dispose()
        eglBase?.release()
        isInitialized.set(false)
    }

    data class SignalingData(val type: String, val sdp: String? = null) {
        fun toJson() = org.json.JSONObject().apply {
            put("type", type)
            sdp?.let { put("sdp", it) }
        }.toString()
    }

    data class IceCandidateData(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String) {
        fun toJson() = org.json.JSONObject().apply {
            put("type", "ice")
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        }.toString()
        companion object {
            fun fromJson(json: String): IceCandidateData {
                val obj = org.json.JSONObject(json)
                return IceCandidateData(
                    obj.optString("sdpMid", null),
                    obj.getInt("sdpMLineIndex"),
                    obj.getString("candidate")
                )
            }
        }
    }
}
