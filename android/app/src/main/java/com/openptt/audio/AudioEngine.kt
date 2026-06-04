package com.openptt.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngine @Inject constructor() {

    companion object {
        private const val TAG = "AudioEngine"
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var sampleRate = 16000 // default 16kHz for voice
    private var frameSize = 960

    private var minBufferSizeIn = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_IN, ENCODING)
    private var minBufferSizeOut = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_OUT, ENCODING)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var udpSocket: DatagramSocket? = null

    private var recordJob: Job? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var serverAddress: InetAddress? = null
    private var serverPort: Int = 9000
    private var currentChannelId: ByteArray = ByteArray(16)
    private var currentUserId: ByteArray = ByteArray(16)

    private var keepAliveJob: Job? = null

    fun init(host: String, port: Int, channelId: String, userId: String, audioQuality: String = "normal") {
        sampleRate = when (audioQuality) {
            "low" -> 8000
            "high" -> 44100
            else -> 16000
        }
        // roughly 20ms frames (fits safely under 1500 byte UDP MTU)
        frameSize = (sampleRate * 0.02).toInt()
        
        minBufferSizeIn = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_IN, ENCODING)
        minBufferSizeOut = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_OUT, ENCODING)

        scope.launch {
            try {
                // Remove http/https scheme if present
                val cleanHost = host.replace("http://", "").replace("https://", "").substringBefore(":")
                serverAddress = InetAddress.getByName(cleanHost)
                serverPort = port
                currentChannelId = uuidToBytes(java.util.UUID.fromString(channelId))
                currentUserId = uuidToBytes(java.util.UUID.fromString(userId))
                Log.d(TAG, "Audio engine initialized for $cleanHost:$port with quality $audioQuality")

                // Start UDP hole punching / keepalive
                startKeepAlive()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve host", e)
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        if (udpSocket == null || udpSocket?.isClosed == true) {
            udpSocket = DatagramSocket()
        }
        
        keepAliveJob = scope.launch {
            val packetData = ByteArray(44) // Just the header
            while (isActive) {
                try {
                    System.arraycopy(currentChannelId, 0, packetData, 0, 16)
                    System.arraycopy(currentUserId, 0, packetData, 16, 16)
                    writeInt(packetData, 32, 0) // Seq
                    writeInt(packetData, 36, (System.currentTimeMillis() and 0xFFFFFFFF).toInt())
                    writeInt(packetData, 40, 1) // Flags = 1 (Keepalive/Ping)
                    
                    serverAddress?.let { addr ->
                        val packet = DatagramPacket(packetData, packetData.size, addr, serverPort)
                        udpSocket?.send(packet)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send keepalive UDP packet", e)
                }
                delay(10000) // 10 seconds
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (recordJob?.isActive == true) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                CHANNEL_IN,
                ENCODING,
                minBufferSizeIn.coerceAtLeast(frameSize * 2)
            )

            if (udpSocket == null || udpSocket?.isClosed == true) {
                udpSocket = DatagramSocket()
            }

            audioRecord?.startRecording()
            Log.d(TAG, "Started recording")

            recordJob = scope.launch {
                val buffer = ShortArray(frameSize)
                // Header: 44 bytes (ChannelID(16), UserID(16), Seq(4), Timestamp(4), Flags(4))
                val packetData = ByteArray(44 + frameSize * 2)
                var seq = 0

                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Build header
                        System.arraycopy(currentChannelId, 0, packetData, 0, 16)
                        System.arraycopy(currentUserId, 0, packetData, 16, 16)
                        writeInt(packetData, 32, seq++)
                        writeInt(packetData, 36, (System.currentTimeMillis() and 0xFFFFFFFF).toInt())
                        writeInt(packetData, 40, 0) // Flags

                        // Convert short to byte
                        var offset = 44
                        for (i in 0 until read) {
                            val sample = buffer[i].toInt()
                            packetData[offset++] = (sample and 0xFF).toByte()
                            packetData[offset++] = ((sample shr 8) and 0xFF).toByte()
                        }

                        // Send via UDP
                        serverAddress?.let { addr ->
                            val packet = DatagramPacket(packetData, offset, addr, serverPort)
                            try {
                                udpSocket?.send(packet)
                                
                                // Log every 50th packet to avoid spamming logcat
                                if (seq % 50 == 0) {
                                    Log.d(TAG, "Sent audio packet: seq=${seq-1}, payloadSize=${offset - 44} bytes")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send UDP packet", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    fun stopRecording() {
        recordJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Stopped recording")
    }

    fun startPlaying() {
        if (playJob?.isActive == true) return

        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                CHANNEL_OUT,
                ENCODING,
                minBufferSizeOut.coerceAtLeast(frameSize * 2),
                AudioTrack.MODE_STREAM
            )

            if (udpSocket == null || udpSocket?.isClosed == true) {
                udpSocket = DatagramSocket()
            }

            audioTrack?.play()
            Log.d(TAG, "Started playing")

            playJob = scope.launch {
                val receiveData = ByteArray(2048) // Safe margin above MTU
                val receivePacket = DatagramPacket(receiveData, receiveData.size)

                while (isActive) {
                    try {
                        udpSocket?.receive(receivePacket)
                        val length = receivePacket.length
                        if (length > 44) {
                            // Extract audio payload (skip 44 byte header)
                            val payloadSize = length - 44
                            val audioData = ShortArray(payloadSize / 2)
                            var offset = 44
                            val gain = 5.0f // 5x software amplification
                            for (i in audioData.indices) {
                                val low = receiveData[offset++].toInt() and 0xFF
                                val high = receiveData[offset++].toInt()
                                var sample = ((high shl 8) or low).toShort().toInt()
                                
                                // Apply gain and clip to 16-bit PCM bounds
                                sample = (sample * gain).toInt()
                                if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE.toInt()
                                else if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE.toInt()
                                
                                audioData[i] = sample.toShort()
                            }
                            audioTrack?.write(audioData, 0, audioData.size)
                            
                            // Log occasionally
                            if (Math.random() < 0.02) {
                                Log.d(TAG, "Received and playing audio packet: payloadSize=$payloadSize bytes")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error receiving UDP packet", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playing", e)
        }
    }

    fun stopPlaying() {
        playJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "Stopped playing")
    }

    fun destroy() {
        keepAliveJob?.cancel()
        stopRecording()
        stopPlaying()
        udpSocket?.close()
        udpSocket = null
    }

    private fun writeInt(array: ByteArray, offset: Int, value: Int) {
        array[offset] = ((value shr 24) and 0xFF).toByte()
        array[offset + 1] = ((value shr 16) and 0xFF).toByte()
        array[offset + 2] = ((value shr 8) and 0xFF).toByte()
        array[offset + 3] = (value and 0xFF).toByte()
    }

    private fun uuidToBytes(uuid: java.util.UUID): ByteArray {
        val bytes = ByteArray(16)
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        for (i in 0..7) {
            bytes[i] = ((msb ushr ((7 - i) * 8)) and 0xFF).toByte()
            bytes[8 + i] = ((lsb ushr ((7 - i) * 8)) and 0xFF).toByte()
        }
        return bytes
    }
}
