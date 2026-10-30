// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import android.os.Build
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.model.*
import tech.kwik.core.QuicClientConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

// --- Data class representing the parsed QR code contents ---
data class ParsedPairingData(
    val fingerprint: String,
    val host: String,
    val port: Int,
    val pairingToken: String
)

// --- Helper to parse the QR Payload ---
// Formatos aceitos:
// 1. Canônico: "<FINGERPRINT_HEX>|<HOST>:<PORTA>|<TOKEN_HEX>"
// 2. Sem delimitadores de fingerprint (com ou sem dois pontos)
// 3. JSON: {"fingerprint": "...", "host": "...", "port": 7443, "token": "..."}
// 4. URI: hyprlink://pair?fp=...&host=...&port=7443&token=...
fun parsePairingQr(rawPayload: String): ParsedPairingData? {
    val payload = rawPayload.trim()

    // Formato 1: JSON
    if (payload.startsWith("{") && payload.endsWith("}")) {
        try {
            val obj = org.json.JSONObject(payload)
            val fp = obj.optString("fingerprint", obj.optString("fp", "")).trim()
            val host = obj.optString("host", obj.optString("ip", "")).trim()
            val port = obj.optInt("port", ConnectionUtils.HYPRLINK_SERVICE_PORT)
            val token = obj.optString("pairing_token", obj.optString("token", "")).trim()
            if (fp.isNotEmpty() && host.isNotEmpty()) {
                return ParsedPairingData(
                    fingerprint = fp,
                    host = host.removePrefix("[").removeSuffix("]"),
                    port = port,
                    pairingToken = token
                )
            }
        } catch (ignored: Exception) {}
    }

    // Formato 2: URI hyprlink://pair?...
    if (payload.startsWith("hyprlink://", ignoreCase = true)) {
        try {
            val uri = android.net.Uri.parse(payload)
            val fp = uri.getQueryParameter("fp") ?: uri.getQueryParameter("fingerprint") ?: ""
            val host = uri.host ?: uri.getQueryParameter("host") ?: ""
            val port = if (uri.port > 0) uri.port else (uri.getQueryParameter("port")?.toIntOrNull() ?: ConnectionUtils.HYPRLINK_SERVICE_PORT)
            val token = uri.getQueryParameter("token") ?: uri.getQueryParameter("pairing_token") ?: ""
            if (fp.isNotEmpty() && host.isNotEmpty()) {
                return ParsedPairingData(
                    fingerprint = fp,
                    host = host,
                    port = port,
                    pairingToken = token
                )
            }
        } catch (ignored: Exception) {}
    }

    // Formato 3: Canônico pipe-separated "<FINGERPRINT>|<HOST>:<PORT>|<TOKEN>"
    val parts = payload.split("|")
    if (parts.size >= 2) {
        val fingerprint = parts[0].trim()
        val hostPort = parts[1].trim()
        val pairingToken = if (parts.size >= 3) parts[2].trim() else ""
        
        val lastColonIndex = hostPort.lastIndexOf(':')
        val host: String
        val port: Int
        if (lastColonIndex != -1) {
            host = hostPort.substring(0, lastColonIndex).trim().removePrefix("[").removeSuffix("]")
            port = hostPort.substring(lastColonIndex + 1).trim().toIntOrNull() ?: ConnectionUtils.HYPRLINK_SERVICE_PORT
        } else {
            host = hostPort.trim().removePrefix("[").removeSuffix("]")
            port = ConnectionUtils.HYPRLINK_SERVICE_PORT
        }
        
        if (fingerprint.isNotEmpty() && host.isNotEmpty()) {
            return ParsedPairingData(
                fingerprint = fingerprint,
                host = host,
                port = port,
                pairingToken = pairingToken
            )
        }
    }

    return null
}

// --- Helper to format hex into standard SHA-256 visual style with colons ---
fun formatFingerprint(hex: String): String {
    val clean = hex.replace(":", "").uppercase().trim()
    if (clean.length != 64) return hex
    return clean.chunked(2).joinToString(":")
}

// --- Hex converter ---
fun hexToBytes(hex: String): ByteArray {
    val cleanHex = hex.replace(":", "").replace(" ", "").trim()
    val len = cleanHex.length
    val data = ByteArray(len / 2)
    for (i in 0 until len step 2) {
        data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) + Character.digit(cleanHex[i + 1], 16)).toByte()
    }
    return data
}

// --- Função pura de decodificação QR a partir de buffer luma (YUV Y plane) ---
fun decodeQrFromLuma(
    luma: ByteArray,
    rowStride: Int,
    width: Int,
    height: Int,
    rotationDegrees: Int
): String? {
    if (width <= 0 || height <= 0 || luma.isEmpty()) return null

    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    val targetLuma: ByteArray
    val dataWidth: Int
    val dataHeight: Int

    when (normalizedRotation) {
        90 -> {
            dataWidth = height
            dataHeight = width
            targetLuma = ByteArray(dataWidth * dataHeight)
            for (y in 0 until height) {
                val rowOffset = y * rowStride
                for (x in 0 until width) {
                    targetLuma[x * height + (height - 1 - y)] = luma[rowOffset + x]
                }
            }
        }
        180 -> {
            dataWidth = width
            dataHeight = height
            targetLuma = ByteArray(dataWidth * dataHeight)
            for (y in 0 until height) {
                val rowOffset = y * rowStride
                val targetRowOffset = (height - 1 - y) * width
                for (x in 0 until width) {
                    targetLuma[targetRowOffset + (width - 1 - x)] = luma[rowOffset + x]
                }
            }
        }
        270 -> {
            dataWidth = height
            dataHeight = width
            targetLuma = ByteArray(dataWidth * dataHeight)
            for (y in 0 until height) {
                val rowOffset = y * rowStride
                for (x in 0 until width) {
                    targetLuma[(width - 1 - x) * height + y] = luma[rowOffset + x]
                }
            }
        }
        else -> {
            targetLuma = luma
            dataWidth = rowStride
            dataHeight = height
        }
    }

    val targetWidth = if (normalizedRotation == 90 || normalizedRotation == 270) height else width
    val targetHeight = if (normalizedRotation == 90 || normalizedRotation == 270) width else height

    val source = PlanarYUVLuminanceSource(
        targetLuma,
        dataWidth,
        dataHeight,
        0,
        0,
        targetWidth,
        targetHeight,
        false
    )

    val reader = QRCodeReader()
    val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    try {
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return reader.decode(bitmap, hints).text
    } catch (_: NotFoundException) {
        try {
            val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
            return reader.decode(invertedBitmap, hints).text
        } catch (_: Exception) {
            return null
        }
    } catch (_: Exception) {
        return null
    }
}

// --- ZXing QR Code Analyzer for CameraX ---
class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val planes = imageProxy.planes
            if (planes.isNotEmpty()) {
                val yPlane = planes[0]
                val buffer = yPlane.buffer
                val remaining = buffer.remaining()
                val luma = ByteArray(remaining)
                buffer.get(luma)

                val result = decodeQrFromLuma(
                    luma = luma,
                    rowStride = yPlane.rowStride,
                    width = imageProxy.width,
                    height = imageProxy.height,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees
                )

                if (result != null) {
                    onQrCodeScanned(result)
                }
            }
        } catch (_: Exception) {
            // Ignorar exceções transitórias de frame
        } finally {
            imageProxy.close()
        }
    }
}


// --- Jetpack Compose CameraX Preview widget ---
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundPreview by remember { mutableStateOf<Preview?>(null) }
    var boundAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var isDisposed by remember { mutableStateOf(false) }

    fun unbindScanner() {
        val provider = boundProvider ?: return
        val preview = boundPreview
        val analysis = boundAnalysis
        try {
            if (preview != null && analysis != null) {
                provider.unbind(preview, analysis)
            } else if (preview != null) {
                provider.unbind(preview)
            } else if (analysis != null) {
                provider.unbind(analysis)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            isDisposed = true
            unbindScanner()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                if (isDisposed) return@addListener
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor, QrCodeAnalyzer { qrText ->
                    val parsed = parsePairingQr(qrText)
                    if (parsed != null) {
                        try {
                            cameraProvider.unbind(preview, imageAnalysis)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    onQrCodeScanned(qrText)
                })

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbind(preview, imageAnalysis)
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    boundProvider = cameraProvider
                    boundPreview = preview
                    boundAnalysis = imageAnalysis
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)
            previewView
        },
        modifier = modifier
    )
}

// --- CBOR Packet Serializer for core.hello ---
fun buildHelloCbor(deviceName: String, pairingTokenHex: String?): ByteArray {
    val baos = ByteArrayOutputStream()
    val builder = CborBuilder()
    val mapBuilder = builder.addMap()
    
    // { id: u64, type: "core.hello", body: <HelloBody>, has_payload: false }
    mapBuilder.put(UnicodeString("id"), UnsignedInteger(1L))
    mapBuilder.put(UnicodeString("type"), UnicodeString("core.hello"))
    
    val bodyMapBuilder = mapBuilder.putMap(UnicodeString("body"))
    bodyMapBuilder.put(UnicodeString("device_name"), UnicodeString(deviceName))
    
    if (!Build.MANUFACTURER.isNullOrBlank()) {
        bodyMapBuilder.put(UnicodeString("manufacturer"), UnicodeString(Build.MANUFACTURER))
    }
    if (!Build.MODEL.isNullOrBlank()) {
        bodyMapBuilder.put(UnicodeString("model"), UnicodeString(Build.MODEL))
    }
    if (!Build.VERSION.RELEASE.isNullOrBlank()) {
        bodyMapBuilder.put(UnicodeString("android"), UnicodeString(Build.VERSION.RELEASE))
    }
    try {
        val appVersion = BuildConfig.VERSION_NAME
        if (!appVersion.isNullOrBlank()) {
            bodyMapBuilder.put(UnicodeString("app_version"), UnicodeString(appVersion))
        }
    } catch (_: Exception) {}
    
    val capabilitiesBuilder = bodyMapBuilder.putArray(UnicodeString("capabilities"))
    capabilitiesBuilder.add(UnicodeString("core"))
    capabilitiesBuilder.add(UnicodeString("clipboard"))
    capabilitiesBuilder.add(UnicodeString("notification"))
    capabilitiesBuilder.add(UnicodeString("media"))
    capabilitiesBuilder.add(UnicodeString("battery"))
    capabilitiesBuilder.add(UnicodeString("share"))
    capabilitiesBuilder.add(UnicodeString("webcam"))
    capabilitiesBuilder.add(UnicodeString("audio"))
    capabilitiesBuilder.add(UnicodeString("input"))
    capabilitiesBuilder.add(UnicodeString("hypr"))
    capabilitiesBuilder.add(UnicodeString("phone"))
    capabilitiesBuilder.add(UnicodeString("phone_status"))
    capabilitiesBuilder.add(UnicodeString("media_session"))
    capabilitiesBuilder.end()
    
    if (!pairingTokenHex.isNullOrBlank()) {
        bodyMapBuilder.put(UnicodeString("pairing_token"), ByteString(hexToBytes(pairingTokenHex)))
    }
    bodyMapBuilder.end()
    
    mapBuilder.put(UnicodeString("has_payload"), SimpleValue.FALSE)
    mapBuilder.end()
    
    CborEncoder(baos).encode(builder.build())
    return baos.toByteArray()
}

// --- CBOR Packet Parser/Verifier for core.hello response ---
fun isResponseCoreHello(cborBytes: ByteArray): Boolean {
    return parseCoreHelloDeviceName(cborBytes) != null
}

data class HandshakeResult(
    val deviceName: String,
    val pcMac: String?
)

// --- Parse core.hello Response and extract workstation device_name ---
fun parseCoreHelloDeviceName(cborBytes: ByteArray): String? {
    try {
        val bais = ByteArrayInputStream(cborBytes)
        val decoded = CborDecoder(bais).decode()
        if (decoded.isEmpty()) return null
        val mainItem = decoded[0]
        if (mainItem is co.nstant.`in`.cbor.model.Map) {
            val typeItem = mainItem.get(UnicodeString("type"))
            if (typeItem is UnicodeString && typeItem.string == "core.hello") {
                val bodyItem = mainItem.get(UnicodeString("body"))
                if (bodyItem is co.nstant.`in`.cbor.model.Map) {
                    val deviceNameItem = bodyItem.get(UnicodeString("device_name"))
                    if (deviceNameItem is UnicodeString) {
                        return deviceNameItem.string
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun parseCoreHelloPcMac(cborBytes: ByteArray): String? {
    try {
        val bais = ByteArrayInputStream(cborBytes)
        val decoded = CborDecoder(bais).decode()
        if (decoded.isEmpty()) return null
        val mainItem = decoded[0]
        if (mainItem is co.nstant.`in`.cbor.model.Map) {
            val typeItem = mainItem.get(UnicodeString("type"))
            if (typeItem is UnicodeString && typeItem.string == "core.hello") {
                val bodyItem = mainItem.get(UnicodeString("body"))
                if (bodyItem is co.nstant.`in`.cbor.model.Map) {
                    val pcMacItem = bodyItem.get(UnicodeString("pc_mac"))
                    if (pcMacItem is UnicodeString) {
                        return pcMacItem.string.trim()
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun parseCoreHelloPcAddrs(cborBytes: ByteArray): List<PcAddr> {
    val result = mutableListOf<PcAddr>()
    try {
        val bais = ByteArrayInputStream(cborBytes)
        val decoded = CborDecoder(bais).decode()
        if (decoded.isEmpty()) return emptyList()
        val mainItem = decoded[0]
        if (mainItem is co.nstant.`in`.cbor.model.Map) {
            val typeItem = mainItem.get(UnicodeString("type"))
            if (typeItem is UnicodeString && typeItem.string == "core.hello") {
                val bodyItem = mainItem.get(UnicodeString("body"))
                if (bodyItem is co.nstant.`in`.cbor.model.Map) {
                    val addrsItem = bodyItem.get(UnicodeString("pc_addrs"))
                    if (addrsItem is co.nstant.`in`.cbor.model.Array) {
                        for (dataItem in addrsItem.dataItems) {
                            if (dataItem is co.nstant.`in`.cbor.model.Map) {
                                val ipItem = dataItem.get(UnicodeString("ip"))
                                val kindItem = dataItem.get(UnicodeString("kind"))
                                val ifaceItem = dataItem.get(UnicodeString("iface"))
                                val ip = (ipItem as? UnicodeString)?.string?.trim()
                                val kind = (kindItem as? UnicodeString)?.string?.trim() ?: "other"
                                val iface = (ifaceItem as? UnicodeString)?.string?.trim() ?: ""
                                if (!ip.isNullOrBlank()) {
                                    result.add(PcAddr(ip = ip, kind = kind, iface = iface))
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}

// --- Coroutine task to execute the full secure QUIC-CBOR Pairing Handshake ---
suspend fun executePairingHandshake(
    context: Context,
    localDeviceName: String,
    data: ParsedPairingData,
    onLog: (String) -> Unit
): HandshakeResult? = withContext(Dispatchers.IO) {
    try {
        onLog("[INFO] Starting handshake pairing process...")
        onLog("[INFO] Host: ${data.host}:${data.port}")
        onLog("[INFO] Pinning TLS to certificate fingerprint: ${formatFingerprint(data.fingerprint)}")
        
        // Load secure local mTLS identity
        val identity = DeviceIdentity.getInstance(context)
        if (!PairingValidationLogic.canPairWithIdentity(identity)) {
            val strings = getAppStrings(LanguageManager.currentLanguage.value)
            val errorMsg = strings.pairingTemporaryIdentityBlocked
            onLog("[ERROR] $errorMsg")
            throw IllegalStateException(errorMsg)
        }
        onLog("[INFO] Presenting client certificate with fingerprint: ${formatFingerprint(identity.fingerprintHex)}")

        val quicConn = ConnectionUtils.connectPinned(
            host = data.host,
            port = data.port,
            applicationProtocol = "hyprlink/1",
            expectedFingerprint = data.fingerprint,
            identity = identity
        )
            
        onLog("[INFO] Connecting to workstation...")
        // ConnectionUtils.connectPinned already called connect() and verified fingerprint pinning!
        
        if (!quicConn.isConnected) {
            onLog("[ERROR] QUIC connection failed to establish.")
            return@withContext null
        }
        
        onLog("[SUCCESS] Secure QUIC connection established (TLS pinned).")
        
        onLog("[INFO] Creating bidirectional stream (ID=0)...")
        val stream = quicConn.createStream(true)
        onLog("[SUCCESS] Stream created successfully. ID: ${stream.streamId}")
        
        onLog("[INFO] Serializing core.hello CBOR body...")
        val cborHello = buildHelloCbor(localDeviceName, data.pairingToken)
        val size = cborHello.size
        
        val frameHeader = byteArrayOf(
            ((size shr 24) and 0xFF).toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte()
        )
        
        onLog("[INFO] Sending core.hello packet (${size} body bytes) with 4-byte big-endian framing...")
        val out = stream.outputStream
        out.write(frameHeader)
        out.write(cborHello)
        out.flush()
        onLog("[DATA] Packet successfully written.")
        
        onLog("[INFO] Waiting for workstation response...")
        val inp = stream.inputStream
        val header = try {
            ConnectionUtils.readWithDeadline(stream, 10_000) {
                val h = ByteArray(4)
                var headerReadBytes = 0
                while (headerReadBytes < 4) {
                    val r = inp.read(h, headerReadBytes, 4 - headerReadBytes)
                    if (r == -1) {
                        throw java.io.IOException("Connection closed by server while reading header.")
                    }
                    headerReadBytes += r
                }
                h
            }
        } catch (e: Exception) {
            onLog("[ERROR] Handshake failed: ${e.message}")
            quicConn.close()
            return@withContext null
        }
        
        val responseSize = ((header[0].toInt() and 0xFF) shl 24) or
                           ((header[1].toInt() and 0xFF) shl 16) or
                           ((header[2].toInt() and 0xFF) shl 8) or
                           (header[3].toInt() and 0xFF)
                           
         if (responseSize <= 0 || responseSize > 5 * 1024 * 1024) {
            onLog("[ERROR] Handshake failed: Invalid response packet size ($responseSize).")
            quicConn.close()
            return@withContext null
        }
        
        onLog("[INFO] Reading response CBOR body of size $responseSize bytes...")
        val responseBody = try {
            ConnectionUtils.readWithDeadline(stream, 10_000) {
                val rb = ByteArray(responseSize)
                var bodyReadBytes = 0
                while (bodyReadBytes < responseSize) {
                    val r = inp.read(rb, bodyReadBytes, responseSize - bodyReadBytes)
                    if (r == -1) {
                        throw java.io.IOException("Connection closed by server while reading body.")
                    }
                    bodyReadBytes += r
                }
                rb
            }
        } catch (e: Exception) {
            onLog("[ERROR] Handshake failed: ${e.message}")
            quicConn.close()
            return@withContext null
        }
        
        onLog("[DATA] Response body successfully read. Parsing CBOR...")
        val workstationName = parseCoreHelloDeviceName(responseBody)
        val pcMac = parseCoreHelloPcMac(responseBody)
        
        quicConn.close()
        
        if (workstationName != null) {
            onLog("[SUCCESS] Workstation '$workstationName' returned valid core.hello! Pairing complete.")
            return@withContext HandshakeResult(workstationName, pcMac)
        } else {
            onLog("[ERROR] Workstation returned unexpected or invalid CBOR payload.")
            return@withContext null
        }
    } catch (e: Exception) {
        onLog("[ERROR] Pairing handshake exception: ${e.javaClass.simpleName}")
        onLog("[ERROR] Reason: ${e.localizedMessage ?: "Unknown error"}")
        return@withContext null
    }
}
