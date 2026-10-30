// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

data class LogRecord(
    val timestamp: String = "",
    val tag: String = "",
    val key: String = "",
    val args: List<String> = emptyList(),
    val raw: String = ""
) {
    fun toNeutralString(): String {
        return if (key.isNotEmpty()) {
            val argsPart = if (args.isNotEmpty()) "|" + args.joinToString("|") else ""
            "[$timestamp] #REC#[$tag][$key$argsPart]"
        } else {
            "[$timestamp] $raw"
        }
    }

    companion object {
        fun parse(line: String): LogRecord {
            val recRegex = Regex("""^\[([^\]]*)\] #REC#\[([^\]]*)\]\[([^\]]*)\]$""")
            val recMatch = recRegex.find(line.trim())
            if (recMatch != null) {
                val ts = recMatch.groupValues[1]
                val tag = recMatch.groupValues[2]
                val keyAndArgs = recMatch.groupValues[3].split("|")
                val key = keyAndArgs[0]
                val args = if (keyAndArgs.size > 1) keyAndArgs.drop(1) else emptyList()
                return LogRecord(timestamp = ts, tag = tag, key = key, args = args)
            }

            val tsRegex = Regex("""^(?:\[(\d{1,2}:\d{2}(?::\d{2})?(?:\.\d{3})?)\]|(\d{1,2}:\d{2}(?::\d{2})?(?:\.\d{3})?))\s*(.*)$""")
            val tsMatch = tsRegex.find(line.trim())
            val ts = tsMatch?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } ?: ""
            val body = tsMatch?.groupValues?.get(3) ?: line.trim()

            return fromBody(ts, body)
        }

        fun fromRaw(ts: String, rawMessage: String): LogRecord {
            return fromBody(ts, rawMessage.trim())
        }

        private fun fromBody(ts: String, body: String): LogRecord {
            // [HYPR] Received workstation event: ...
            val hyprMatch = Regex("""^\[HYPR\] (?:Received workstation event:|收到工作站事件:)\s*(.*)$""").find(body)
            if (hyprMatch != null) {
                return LogRecord(ts, "HYPR", "hypr_event", listOf(hyprMatch.groupValues[1].trim()))
            }

            // [RECV] Incoming stream packet type: ... (routerPrefix: ...)
            val recvMatch = Regex("""^\[RECV\] (?:Incoming stream packet type:|收到流数据包类型:)\s*([^\s]+)\s*\(routerPrefix:\s*([^\)]+)\)""").find(body)
            if (recvMatch != null) {
                return LogRecord(ts, "RECV", "recv_stream_packet", listOf(recvMatch.groupValues[1], recvMatch.groupValues[2]))
            }

            // [CONN] Connected to ...
            val connMatch = Regex("""^\[CONN\] (?:Connected to|已连接到工作站)\s*(.*)$""").find(body)
            if (connMatch != null) {
                return LogRecord(ts, "CONN", "connected_to", listOf(connMatch.groupValues[1].trim()))
            }

            // [CONN] Registered active connection!
            if (body.contains("[CONN] Registered active connection!") || body.contains("[CONN] 已注册活动连接！")) {
                return LogRecord(ts, "CONN", "registered_conn")
            }

            // [CONN] Attempt → host:port
            val attemptMatch = Regex("""^\[CONN\] (?:Attempt →|尝试连接 →)\s*(.*?):(\d+)""").find(body)
            if (attemptMatch != null) {
                return LogRecord(ts, "CONN", "conn_attempt", listOf(attemptMatch.groupValues[1], attemptMatch.groupValues[2]))
            }

            // [CONN] Retry in Xs ...
            val retryMatch = Regex("""^\[CONN\] (?:Retry in|将在)\s*(\d+)s?""").find(body)
            if (retryMatch != null) {
                return LogRecord(ts, "CONN", "conn_retry", listOf(retryMatch.groupValues[1]))
            }

            // [SERVICE] Connection loop started (station=...)
            val sLoopMatch = Regex("""^\[SERVICE\] (?:Connection loop started \(station=|(?:连接循环已启动 \(工作站=))(.*?)\)""").find(body)
            if (sLoopMatch != null) {
                return LogRecord(ts, "SERVICE", "conn_service_loop", listOf(sLoopMatch.groupValues[1]))
            }

            // [SERVICE] Successfully connected to workstation DEV (NAME)
            val sSuccessMatch = Regex("""^\[SERVICE\] (?:Successfully connected to workstation|成功连接到工作站)\s*(.*?)\s*\((.*?)\)""").find(body)
            if (sSuccessMatch != null) {
                return LogRecord(ts, "SERVICE", "conn_service_success", listOf(sSuccessMatch.groupValues[1], sSuccessMatch.groupValues[2]))
            }

            // [TAG] tx N bytes
            val txMatch = Regex("""^\[(CLIP|AUDIO|DATA|CORE)\] (?:tx|发送)\s*(\d+)\s*(?:bytes|字节)""").find(body)
            if (txMatch != null) {
                return LogRecord(ts, txMatch.groupValues[1], "tx_bytes", listOf(txMatch.groupValues[1], txMatch.groupValues[2]))
            }

            // [TAG] rx DESC
            val rxMatch = Regex("""^\[(CLIP|AUDIO|DATA|CORE)\] (?:rx|接收)\s*(.*)""").find(body)
            if (rxMatch != null) {
                return LogRecord(ts, rxMatch.groupValues[1], "rx_stream", listOf(rxMatch.groupValues[1], rxMatch.groupValues[2].trim()))
            }

            // [NOTIF] Mirrored notification from APP: TITLE
            val notifMirroredMatch = Regex("""^\[NOTIF\] (?:Mirrored notification from|镜像来自)\s*(.*?)(?::| 的通知:)\s*(.*)""").find(body)
            if (notifMirroredMatch != null) {
                return LogRecord(ts, "NOTIF", "notif_mirrored", listOf(notifMirroredMatch.groupValues[1].trim(), notifMirroredMatch.groupValues[2].trim()))
            }

            // [NOTIF] Notification dismissed: KEY
            val notifDismissedMatch = Regex("""^\[NOTIF\] (?:Notification dismissed:|通知已关闭:)\s*(.*)""").find(body)
            if (notifDismissedMatch != null) {
                return LogRecord(ts, "NOTIF", "notif_dismissed", listOf(notifDismissedMatch.groupValues[1].trim()))
            }

            // [NOTIF] Reply sent for key KEY
            val notifReplyMatch = Regex("""^\[NOTIF\] (?:Reply sent for key|已回复通知:)\s*(.*)""").find(body)
            if (notifReplyMatch != null) {
                return LogRecord(ts, "NOTIF", "notif_reply_sent", listOf(notifReplyMatch.groupValues[1].trim()))
            }

            // [NOTIF] Triggered action IDX ('TITLE') for key KEY
            val notifActionMatch = Regex("""^\[NOTIF\] (?:Triggered action|已触发动作)\s*(\d+)\s*\('(.*?)'\)(?: for key |，键: )(.*)""").find(body)
            if (notifActionMatch != null) {
                return LogRecord(ts, "NOTIF", "notif_action_triggered", listOf(notifActionMatch.groupValues[1], notifActionMatch.groupValues[2], notifActionMatch.groupValues[3]))
            }

            // [NOTIF] Posted notification from workstation: TITLE
            val notifPostMatch = Regex("""^\[NOTIF\] (?:Posted notification from workstation:|已发布来自工作站的通知:)\s*(.*)""").find(body)
            if (notifPostMatch != null) {
                return LogRecord(ts, "NOTIF", "notif_posted", listOf(notifPostMatch.groupValues[1].trim()))
            }

            // [SHARE] Initiating file send: NAME (SIZE bytes), packetId=ID
            val shareSendMatch = Regex("""^\[SHARE\] (?:Initiating file send:|开始发送文件:)\s*(.*?) \((\d+) (?:bytes|字节)\), (?:packetId=|数据包ID=)(\d+)""").find(body)
            if (shareSendMatch != null) {
                return LogRecord(ts, "SHARE", "share_init_send", listOf(shareSendMatch.groupValues[1], shareSendMatch.groupValues[2], shareSendMatch.groupValues[3]))
            }

            // [SHARE] Successfully sent file: NAME. Calculated local SHA-256: HASH
            val shareSendSuccessMatch = Regex("""^\[SHARE\] (?:Successfully sent file:|文件发送成功:)\s*(.*?)\.\s*(?:Calculated local SHA-256:|本地计算 SHA-256:)\s*(.*)""").find(body)
            if (shareSendSuccessMatch != null) {
                return LogRecord(ts, "SHARE", "share_send_success", listOf(shareSendSuccessMatch.groupValues[1], shareSendSuccessMatch.groupValues[2].trim()))
            }

            // [SHARE] Received file announcement: NAME (SIZE bytes), packetId=ID
            val shareRecvMatch = Regex("""^\[SHARE\] (?:Received file announcement:|收到文件公告:)\s*(.*?) \((\d+) (?:bytes|字节)\), (?:packetId=|数据包ID=)(\d+)""").find(body)
            if (shareRecvMatch != null) {
                return LogRecord(ts, "SHARE", "share_file_received", listOf(shareRecvMatch.groupValues[1], shareRecvMatch.groupValues[2], shareRecvMatch.groupValues[3]))
            }

            // [SHARE] Download id=ID VERIFIED ✓ (SHA256: HASH)
            val shareVerMatch = Regex("""^\[SHARE\] (?:Download id=|下载 id=)(\d+) (?:VERIFIED ✓|已验证 ✓) \(SHA256: (.*?)\)""").find(body)
            if (shareVerMatch != null) {
                return LogRecord(ts, "SHARE", "share_download_verified", listOf(shareVerMatch.groupValues[1], shareVerMatch.groupValues[2].trim()))
            }

            // [SHARE] Download id=ID failed on PC side: ERR
            val shareFailMatch = Regex("""^\[SHARE\] (?:Download id=|下载 id=)(\d+) (?:failed on PC side:|在电脑端失败:)\s*(.*)""").find(body)
            if (shareFailMatch != null) {
                return LogRecord(ts, "SHARE", "share_download_failed", listOf(shareFailMatch.groupValues[1], shareFailMatch.groupValues[2].trim()))
            }

            // [SHARE] Download id=ID ERROR: SHA256 mismatch. Local: L, Remote: R. Deleted file.
            val shareMismatchMatch = Regex("""^\[SHARE\] (?:Download id=|下载 id=)(\d+) (?:ERROR: SHA256 mismatch\.|错误: SHA256 不匹配。)\s*(?:Local:|本地:)\s*(.*?), (?:Remote:|远程:)\s*(.*?)\.\s*(?:Deleted file\.|已删除文件。)""").find(body)
            if (shareMismatchMatch != null) {
                return LogRecord(ts, "SHARE", "share_download_mismatch", listOf(shareMismatchMatch.groupValues[1], shareMismatchMatch.groupValues[2].trim(), shareMismatchMatch.groupValues[3].trim()))
            }

            // [SHARE] Error sending file: MSG
            val shareErrMatch = Regex("""^\[SHARE\] (?:Error sending file:|发送文件出错:)\s*(.*)""").find(body)
            if (shareErrMatch != null) {
                return LogRecord(ts, "SHARE", "share_error_sending", listOf(shareErrMatch.groupValues[1].trim()))
            }

            // [CLIP] PNG image sent to PC: N KB
            val clipSentMatch = Regex("""^\[CLIP\] (?:PNG image sent to PC:|Imagem PNG enviada para o PC:|已向电脑发送 PNG 图片:)\s*(\d+)\s*KB""").find(body)
            if (clipSentMatch != null) {
                return LogRecord(ts, "CLIP", "clip_img_sent", listOf(clipSentMatch.groupValues[1]))
            }

            // [CLIP] Image received from PC: N KB
            val clipRecvMatch = Regex("""^\[CLIP\] (?:Image received from PC:|imagem recebida do PC:|从电脑接收到图片:)\s*(\d+)\s*KB""").find(body)
            if (clipRecvMatch != null) {
                return LogRecord(ts, "CLIP", "clip_img_received", listOf(clipRecvMatch.groupValues[1]))
            }

            // [CLIPBOARD] Copied from workstation!
            if (body.contains("[CLIPBOARD] Copied from workstation!") || body.contains("[CLIPBOARD] 已从工作站复制！")) {
                return LogRecord(ts, "CLIPBOARD", "clip_copied_workstation")
            }

            // [BATTERY] Sending phone battery state: level=L, charging=C
            val batSendMatch = Regex("""^\[BATTERY\] (?:Sending phone battery state:|发送手机电量状态:)\s*level=(\d+),\s*charging=(.*)""").find(body)
            if (batSendMatch != null) {
                return LogRecord(ts, "BATTERY", "battery_phone_sending", listOf(batSendMatch.groupValues[1], batSendMatch.groupValues[2].trim()))
            }

            // [BATTERY] Workstation Battery: L% (Charging: C)
            val batWsMatch = Regex("""^\[BATTERY\] (?:Workstation Battery:|工作站电量:)\s*(\d+)%\s*\((?:Charging:|充电状态:)\s*(.*?)\)""").find(body)
            if (batWsMatch != null) {
                return LogRecord(ts, "BATTERY", "battery_workstation", listOf(batWsMatch.groupValues[1], batWsMatch.groupValues[2].trim()))
            }

            // [MEDIA] Now Playing updated: TITLE - ARTIST [STATUS] (art_id=ART)
            val mediaMatch = Regex("""^\[MEDIA\] (?:Now Playing updated:|正在播放已更新:)\s*(.*?)\s*-\s*(.*?)\s*\[(.*?)\](?:\s*\(art_id=(.*?)\))?""").find(body)
            if (mediaMatch != null) {
                return LogRecord(ts, "MEDIA", "media_now_playing", listOf(mediaMatch.groupValues[1], mediaMatch.groupValues[2], mediaMatch.groupValues[3], mediaMatch.groupValues[4]))
            }

            // [MEDIA] Sending media command: CMD
            val mediaCmdMatch = Regex("""^\[MEDIA\] (?:Sending media command:|发送媒体控制指令:)\s*(.*)""").find(body)
            if (mediaCmdMatch != null) {
                return LogRecord(ts, "MEDIA", "media_command", listOf(mediaCmdMatch.groupValues[1].trim()))
            }

            // [SYNC] Sending screenshot: NAME (N KB)...
            val syncSendMatch = Regex("""^\[SYNC\] (?:A enviar screenshot:|Sending screenshot:|正在发送截图:)\s*(.*?)\s*\((\d+)\s*KB\)\.\.\.""").find(body)
            if (syncSendMatch != null) {
                return LogRecord(ts, "SYNC", "sync_screenshot_sending", listOf(syncSendMatch.groupValues[1], syncSendMatch.groupValues[2]))
            }

            // [SYNC] Screenshot sent: NAME (N KB)
            val syncSentMatch = Regex("""^\[SYNC\] (?:screenshot enviado:|Screenshot sent:|截图已发送:)\s*(.*?)\s*\((\d+)\s*KB\)""").find(body)
            if (syncSentMatch != null) {
                return LogRecord(ts, "SYNC", "sync_screenshot_sent", listOf(syncSentMatch.groupValues[1], syncSentMatch.groupValues[2]))
            }

            // [SYNC] Screenshot ignored, no connection
            if (body.contains("[SYNC] screenshot ignorado, sem conexão") || body.contains("[SYNC] Screenshot ignored, no connection") || body.contains("[SYNC] 已忽略截图，无网络连接")) {
                return LogRecord(ts, "SYNC", "sync_screenshot_ignored")
            }

            // [WOL] Magic packet sent to MAC (destinations: DESTS)
            val wolMatch = Regex("""^\[WOL\] (?:magic packet enviado pra|Magic packet sent to|已向)\s*(.*?)(?: \(destinos:| \(destinations:| 发送唤醒数据包 \(目标:)\s*(.*?)\)""").find(body)
            if (wolMatch != null) {
                return LogRecord(ts, "WOL", "wol_magic_sent", listOf(wolMatch.groupValues[1].trim(), wolMatch.groupValues[2].trim()))
            }

            // [WOL] Timeout waiting for PC to wake.
            if (body.contains("[WOL] Timeout ao esperar pelo PC acordar") || body.contains("[WOL] Timeout waiting for PC to wake") || body.contains("[WOL] 等待电脑唤醒超时")) {
                return LogRecord(ts, "WOL", "wol_timeout")
            }

            // [WOL] User cancelled waiting for WoL.
            if (body.contains("[WOL] Utilizador cancelou a espera por WoL") || body.contains("[WOL] User cancelled waiting for WoL") || body.contains("[WOL] 用户取消等待网络唤醒")) {
                return LogRecord(ts, "WOL", "wol_cancelled")
            }

            // [WEBCAM] No hardware HEVC encoder — using H.264
            if (body.contains("[WEBCAM] Sem encoder HEVC por hardware") || body.contains("[WEBCAM] No hardware HEVC encoder") || body.contains("[WEBCAM] 无硬件 HEVC 编码器")) {
                return LogRecord(ts, "WEBCAM", "webcam_hevc_fallback")
            }

            // [WEBCAM] Warning: Resolution WxH not listed as natively supported...
            val camResMatch = Regex("""^\[WEBCAM\] (?:Aviso: Resolução|Warning: Resolution|警告: 分辨率)\s*(\d+)x(\d+)""").find(body)
            if (camResMatch != null) {
                return LogRecord(ts, "WEBCAM", "webcam_resolution_warning", listOf(camResMatch.groupValues[1], camResMatch.groupValues[2]))
            }

            // [WEBCAM] Microphone: no active connection to PC
            if (body.contains("[WEBCAM] Microfone: sem conexão ativa") || body.contains("[WEBCAM] Microphone: no active connection") || body.contains("[WEBCAM] 麦克风: 未建立与电脑的有效连接")) {
                return LogRecord(ts, "WEBCAM", "webcam_mic_no_conn")
            }

            // [WEBCAM] Microphone permission not granted
            if (body.contains("[WEBCAM] Permissão de microfone não concedida") || body.contains("[WEBCAM] Microphone permission not granted") || body.contains("[WEBCAM] 未授予麦克风权限")) {
                return LogRecord(ts, "WEBCAM", "webcam_mic_denied")
            }

            // Readable journal items
            if (body == "Workstation paired" || body == "Estação emparelhada" || body == "Estação pareada" || body == "工作站已配对") {
                return LogRecord(ts, "INFO", "station_paired")
            }
            val fileSentMatch = Regex("""^(?:File sent:|Ficheiro enviado:|Arquivo enviado:|文件已发送:)\s*(.*)""").find(body)
            if (fileSentMatch != null) {
                return LogRecord(ts, "INFO", "file_sent", listOf(fileSentMatch.groupValues[1].trim()))
            }
            if (body == "Screenshot sent" || body == "Captura enviada" || body == "Captura de tela enviada" || body == "截图已发送") {
                return LogRecord(ts, "INFO", "screenshot_sent")
            }
            val notifMirroredReadableMatch = Regex("""^(?:Notification mirrored:|Notificação espelhada:|通知已镜像:)\s*(.*)""").find(body)
            if (notifMirroredReadableMatch != null) {
                return LogRecord(ts, "INFO", "notif_mirrored_readable", listOf(notifMirroredReadableMatch.groupValues[1].trim()))
            }
            if (body == "PC connected" || body == "PC ligado" || body == "PC conectado" || body == "电脑已连接") {
                return LogRecord(ts, "INFO", "pc_connected")
            }
            if (body == "Connection lost" || body == "Ligação perdida" || body == "Conexão perdida" || body == "连接已断开") {
                return LogRecord(ts, "INFO", "connection_lost")
            }

            // [WARN] MSG
            val warnMatch = Regex("""^\[WARN\]\s*(.*)$""").find(body)
            if (warnMatch != null) {
                return LogRecord(ts, "WARN", "warn_generic", listOf(warnMatch.groupValues[1].trim()))
            }

            // [ERROR] MSG
            val errMatch = Regex("""^\[ERROR\]\s*(.*)$""").find(body)
            if (errMatch != null) {
                return LogRecord(ts, "ERROR", "error_generic", listOf(errMatch.groupValues[1].trim()))
            }

            // Unknown record
            val tagMatch = Regex("""^\[([A-Z0-9_-]+)\]""").find(body)
            val extractedTag = tagMatch?.groupValues?.get(1) ?: ""
            return LogRecord(ts, extractedTag, "", emptyList(), body)
        }
    }
}

object TerminalLogTranslator {

    fun translate(line: String, lang: AppLanguage, isTechnical: Boolean = true): String {
        val record = LogRecord.parse(line)
        val text = translate(record, lang, isTechnical)
        return if (record.timestamp.isNotEmpty()) {
            "${record.timestamp} $text"
        } else {
            text
        }
    }

    fun translate(record: LogRecord, lang: AppLanguage, isTechnical: Boolean = true): String {
        val effectiveLang = if (lang == AppLanguage.SYSTEM) {
            val code = LanguageManager.getEffectiveLanguageCode().lowercase()
            when {
                code.startsWith("zh") -> AppLanguage.ZH_CN
                code.startsWith("es") -> AppLanguage.ES_ES
                code.startsWith("pt") && (code.contains("pt") || code.contains("ao") || code.contains("mz")) -> AppLanguage.PT_PT
                code.startsWith("pt") -> AppLanguage.PT_BR
                else -> AppLanguage.EN_UK
            }
        } else {
            lang
        }

        if (record.key.isEmpty()) {
            return record.raw
        }

        // Rule for technical detail / terminal:
        // In ZH_CN -> Chinese table
        // In ANY other language -> English table
        if (isTechnical) {
            return if (effectiveLang == AppLanguage.ZH_CN) {
                formatZh(record)
            } else {
                formatEn(record)
            }
        }

        // Readable journal: follows app language in all 5 languages
        return when (effectiveLang) {
            AppLanguage.ZH_CN -> formatZh(record)
            AppLanguage.PT_PT -> formatPtPt(record)
            AppLanguage.PT_BR -> formatPtBr(record)
            AppLanguage.ES_ES -> formatEsEs(record)
            else -> formatEn(record)
        }
    }

    fun formatEn(record: LogRecord): String {
        val a = record.args
        return when (record.key) {
            "hypr_event" -> "[HYPR] Received workstation event: ${translateHyprEventEn(a.getOrElse(0) { "" })}"
            "recv_stream_packet" -> "[RECV] Incoming stream packet type: ${a.getOrElse(0) { "" }} (routerPrefix: ${a.getOrElse(1) { "" }})"
            "connected_to" -> "[CONN] Connected to ${a.getOrElse(0) { "" }}"
            "registered_conn" -> "[CONN] Registered active connection!"
            "conn_attempt" -> "[CONN] Attempt → ${a.getOrElse(0) { "" }}:${a.getOrElse(1) { "" }}"
            "conn_retry" -> "[CONN] Retry in ${a.getOrElse(0) { "" }}s (reason: connection failed or closed)"
            "conn_service_loop" -> "[SERVICE] Connection loop started (station=${a.getOrElse(0) { "" }})"
            "conn_service_success" -> "[SERVICE] Successfully connected to workstation ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }})"
            "tx_bytes" -> "[${a.getOrElse(0) { record.tag }}] tx ${a.getOrElse(1) { "" }} bytes"
            "rx_stream" -> "[${a.getOrElse(0) { record.tag }}] rx ${a.getOrElse(1) { "" }}"
            "notif_mirrored" -> "[NOTIF] Mirrored notification from ${a.getOrElse(0) { "" }}: ${a.getOrElse(1) { "" }}"
            "notif_dismissed" -> "[NOTIF] Notification dismissed: ${a.getOrElse(0) { "" }}"
            "notif_reply_sent" -> "[NOTIF] Reply sent for key ${a.getOrElse(0) { "" }}"
            "notif_action_triggered" -> "[NOTIF] Triggered action ${a.getOrElse(0) { "" }} ('${a.getOrElse(1) { "" }}') for key ${a.getOrElse(2) { "" }}"
            "notif_posted" -> "[NOTIF] Posted notification from workstation: ${a.getOrElse(0) { "" }}"
            "share_init_send" -> "[SHARE] Initiating file send: ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }} bytes), packetId=${a.getOrElse(2) { "" }}"
            "share_send_success" -> "[SHARE] Successfully sent file: ${a.getOrElse(0) { "" }}. Calculated local SHA-256: ${a.getOrElse(1) { "" }}"
            "share_file_received" -> "[SHARE] Received file announcement: ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }} bytes), packetId=${a.getOrElse(2) { "" }}"
            "share_download_verified" -> "[SHARE] Download id=${a.getOrElse(0) { "" }} VERIFIED ✓ (SHA256: ${a.getOrElse(1) { "" }})"
            "share_download_failed" -> "[SHARE] Download id=${a.getOrElse(0) { "" }} failed on PC side: ${a.getOrElse(1) { "" }}"
            "share_download_mismatch" -> "[SHARE] Download id=${a.getOrElse(0) { "" }} ERROR: SHA256 mismatch. Local: ${a.getOrElse(1) { "" }}, Remote: ${a.getOrElse(2) { "" }}. Deleted file."
            "share_error_sending" -> "[SHARE] Error sending file: ${a.getOrElse(0) { "" }}"
            "clip_img_sent" -> "[CLIP] PNG image sent to PC: ${a.getOrElse(0) { "" }} KB"
            "clip_img_received" -> "[CLIP] Image received from PC: ${a.getOrElse(0) { "" }} KB"
            "clip_copied_workstation" -> "[CLIPBOARD] Copied from workstation!"
            "battery_phone_sending" -> "[BATTERY] Sending phone battery state: level=${a.getOrElse(0) { "" }}, charging=${a.getOrElse(1) { "" }}"
            "battery_workstation" -> "[BATTERY] Workstation Battery: ${a.getOrElse(0) { "" }}% (Charging: ${a.getOrElse(1) { "" }})"
            "media_now_playing" -> {
                val art = a.getOrElse(3) { "" }
                "[MEDIA] Now Playing updated: ${a.getOrElse(0) { "" }} - ${a.getOrElse(1) { "" }} [${a.getOrElse(2) { "" }}]${if (art.isNotEmpty()) " (art_id=$art)" else ""}"
            }
            "media_command" -> "[MEDIA] Sending media command: ${a.getOrElse(0) { "" }}"
            "sync_screenshot_sending" -> "[SYNC] Sending screenshot: ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }} KB)..."
            "sync_screenshot_sent" -> "[SYNC] Screenshot sent: ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }} KB)"
            "sync_screenshot_ignored" -> "[SYNC] Screenshot ignored, no connection"
            "wol_magic_sent" -> "[WOL] Magic packet sent to ${a.getOrElse(0) { "" }} (destinations: ${a.getOrElse(1) { "" }})"
            "wol_timeout" -> "[WOL] Timeout waiting for PC to wake."
            "wol_cancelled" -> "[WOL] User cancelled waiting for WoL."
            "webcam_hevc_fallback" -> "[WEBCAM] No hardware HEVC encoder — using H.264"
            "webcam_resolution_warning" -> "[WEBCAM] Warning: Resolution ${a.getOrElse(0) { "" }}x${a.getOrElse(1) { "" }} not listed as natively supported. CameraX will adjust."
            "webcam_mic_no_conn" -> "[WEBCAM] Microphone: no active connection to PC"
            "webcam_mic_denied" -> "[WEBCAM] Microphone permission not granted"
            "station_paired" -> "Workstation paired"
            "file_sent" -> "File sent: ${a.getOrElse(0) { "" }}"
            "screenshot_sent" -> "Screenshot sent"
            "notif_mirrored_readable" -> "Notification mirrored: ${a.getOrElse(0) { "" }}"
            "pc_connected" -> "PC connected"
            "connection_lost" -> "Connection lost"
            "warn_generic" -> "[WARN] ${a.getOrElse(0) { "" }}"
            "error_generic" -> "[ERROR] ${a.getOrElse(0) { "" }}"
            else -> record.raw
        }
    }

    fun formatZh(record: LogRecord): String {
        val a = record.args
        return when (record.key) {
            "hypr_event" -> "[HYPR] 收到工作站事件: ${translateHyprEventZh(a.getOrElse(0) { "" })}"
            "recv_stream_packet" -> "[RECV] 收到流数据包类型: ${a.getOrElse(0) { "" }} (routerPrefix: ${a.getOrElse(1) { "" }})"
            "connected_to" -> "[CONN] 已连接到工作站 ${a.getOrElse(0) { "" }}"
            "registered_conn" -> "[CONN] 已注册活动连接！"
            "conn_attempt" -> "[CONN] 尝试连接 → ${a.getOrElse(0) { "" }}:${a.getOrElse(1) { "" }}"
            "conn_retry" -> "[CONN] 将在 ${a.getOrElse(0) { "" }} 秒后重试 (原因: 连接失败或已关闭)"
            "conn_service_loop" -> "[SERVICE] 连接循环已启动 (工作站=${a.getOrElse(0) { "" }})"
            "conn_service_success" -> "[SERVICE] 成功连接到工作站 ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }})"
            "tx_bytes" -> "[${a.getOrElse(0) { record.tag }}] 发送 ${a.getOrElse(1) { "" }} 字节"
            "rx_stream" -> "[${a.getOrElse(0) { record.tag }}] 接收 ${a.getOrElse(1) { "" }}"
            "notif_mirrored" -> "[NOTIF] 镜像来自 ${a.getOrElse(0) { "" }} 的通知: ${a.getOrElse(1) { "" }}"
            "notif_dismissed" -> "[NOTIF] 通知已关闭: ${a.getOrElse(0) { "" }}"
            "notif_reply_sent" -> "[NOTIF] 已回复通知: ${a.getOrElse(0) { "" }}"
            "notif_action_triggered" -> "[NOTIF] 已触发动作 ${a.getOrElse(0) { "" }} ('${a.getOrElse(1) { "" }}')，键: ${a.getOrElse(2) { "" }}"
            "notif_posted" -> "[NOTIF] 已发布来自工作站的通知: ${a.getOrElse(0) { "" }}"
            "share_init_send" -> "[SHARE] 开始发送文件: ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }} 字节), 数据包ID=${a.getOrElse(2) { "" }}"
            "share_send_success" -> "[SHARE] 文件发送成功: ${a.getOrElse(0) { "" }}。本地计算 SHA-256: ${a.getOrElse(1) { "" }}"
            "share_file_received" -> "[SHARE] 收到文件公告: ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }} 字节), 数据包ID=${a.getOrElse(2) { "" }}"
            "share_download_verified" -> "[SHARE] 下载 id=${a.getOrElse(0) { "" }} 已验证 ✓ (SHA256: ${a.getOrElse(1) { "" }})"
            "share_download_failed" -> "[SHARE] 下载 id=${a.getOrElse(0) { "" }} 在电脑端失败: ${a.getOrElse(1) { "" }}"
            "share_download_mismatch" -> "[SHARE] 下载 id=${a.getOrElse(0) { "" }} 错误: SHA256 不匹配。本地: ${a.getOrElse(1) { "" }}, 远程: ${a.getOrElse(2) { "" }}。已删除文件。"
            "share_error_sending" -> "[SHARE] 发送文件出错: ${a.getOrElse(0) { "" }}"
            "clip_img_sent" -> "[CLIP] 已向电脑发送 PNG 图片: ${a.getOrElse(0) { "" }} KB"
            "clip_img_received" -> "[CLIP] 从电脑接收到图片: ${a.getOrElse(0) { "" }} KB"
            "clip_copied_workstation" -> "[CLIPBOARD] 已从工作站复制！"
            "battery_phone_sending" -> "[BATTERY] 发送手机电量状态: 电量=${a.getOrElse(0) { "" }}, 充电=${a.getOrElse(1) { "" }}"
            "battery_workstation" -> "[BATTERY] 工作站电量: ${a.getOrElse(0) { "" }}% (充电状态: ${a.getOrElse(1) { "" }})"
            "media_now_playing" -> {
                val art = a.getOrElse(3) { "" }
                "[MEDIA] 正在播放已更新: ${a.getOrElse(0) { "" }} - ${a.getOrElse(1) { "" }} [${a.getOrElse(2) { "" }}]${if (art.isNotEmpty()) " (art_id=$art)" else ""}"
            }
            "media_command" -> "[MEDIA] 发送媒体控制指令: ${a.getOrElse(0) { "" }}"
            "sync_screenshot_sending" -> "[SYNC] 正在发送截图: ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }} KB)..."
            "sync_screenshot_sent" -> "[SYNC] 截图已发送: ${a.getOrElse(0) { "" }} (${a.getOrElse(1) { "" }} KB)"
            "sync_screenshot_ignored" -> "[SYNC] 已忽略截图，无网络连接"
            "wol_magic_sent" -> "[WOL] 已向 ${a.getOrElse(0) { "" }} 发送唤醒数据包 (目标: ${a.getOrElse(1) { "" }})"
            "wol_timeout" -> "[WOL] 等待电脑唤醒超时。"
            "wol_cancelled" -> "[WOL] 用户取消等待网络唤醒。"
            "webcam_hevc_fallback" -> "[WEBCAM] 无硬件 HEVC 编码器 — 使用 H.264"
            "webcam_resolution_warning" -> "[WEBCAM] 警告: 分辨率 ${a.getOrElse(0) { "" }}x${a.getOrElse(1) { "" }} 未列为原生支持。CameraX 将尝试最佳匹配。"
            "webcam_mic_no_conn" -> "[WEBCAM] 麦克风: 未建立与电脑的有效连接"
            "webcam_mic_denied" -> "[WEBCAM] 未授予麦克风权限"
            "station_paired" -> "工作站已配对"
            "file_sent" -> "文件已发送: ${a.getOrElse(0) { "" }}"
            "screenshot_sent" -> "截图已发送"
            "notif_mirrored_readable" -> "通知已镜像: ${a.getOrElse(0) { "" }}"
            "pc_connected" -> "电脑已连接"
            "connection_lost" -> "连接已断开"
            "warn_generic" -> "[WARN] ${a.getOrElse(0) { "" }}"
            "error_generic" -> "[ERROR] ${a.getOrElse(0) { "" }}"
            else -> record.raw
        }
    }

    fun formatPtPt(record: LogRecord): String {
        val a = record.args
        return when (record.key) {
            "station_paired" -> "Estação emparelhada"
            "file_sent" -> "Ficheiro enviado: ${a.getOrElse(0) { "" }}"
            "screenshot_sent" -> "Captura enviada"
            "notif_mirrored_readable" -> "Notificação espelhada: ${a.getOrElse(0) { "" }}"
            "pc_connected" -> "PC ligado"
            "connection_lost" -> "Ligação perdida"
            else -> formatEn(record)
        }
    }

    fun formatPtBr(record: LogRecord): String {
        val a = record.args
        return when (record.key) {
            "station_paired" -> "Estação pareada"
            "file_sent" -> "Arquivo enviado: ${a.getOrElse(0) { "" }}"
            "screenshot_sent" -> "Captura de tela enviada"
            "notif_mirrored_readable" -> "Notificação espelhada: ${a.getOrElse(0) { "" }}"
            "pc_connected" -> "PC conectado"
            "connection_lost" -> "Conexão perdida"
            else -> formatEn(record)
        }
    }

    fun formatEsEs(record: LogRecord): String {
        val a = record.args
        return when (record.key) {
            "station_paired" -> "Estación emparejada"
            "file_sent" -> "Archivo enviado: ${a.getOrElse(0) { "" }}"
            "screenshot_sent" -> "Captura enviada"
            "notif_mirrored_readable" -> "Notificación reflejada: ${a.getOrElse(0) { "" }}"
            "pc_connected" -> "PC conectado"
            "connection_lost" -> "Conexión perdida"
            else -> formatEn(record)
        }
    }

    private fun translateHyprEventEn(rawEvent: String): String {
        val clean = rawEvent.replace(Regex("""^windowtitlev?2?>>.*"""), "activewindow")
            .replace(Regex(""">>.*"""), "")
            .lowercase().trim()
        return when (clean) {
            "activewindow", "activewindowv2", "windowtitle", "windowtitlev2" -> "active window changed"
            "workspace", "workspacev2" -> "workspace changed"
            "focusedmon" -> "focused monitor changed"
            "destroyworkspace" -> "workspace destroyed"
            "createworkspace" -> "workspace created"
            "fullscreen" -> "fullscreen toggled"
            "monitoradded", "monitoraddedv2" -> "monitor added"
            "monitorremoved" -> "monitor removed"
            "openwindow" -> "window opened"
            "closewindow" -> "window closed"
            "movewindow", "movewindowv2" -> "window moved"
            else -> rawEvent
        }
    }

    private fun translateHyprEventZh(rawEvent: String): String {
        val clean = rawEvent.replace(Regex("""^windowtitlev?2?>>.*"""), "activewindow")
            .replace(Regex(""">>.*"""), "")
            .lowercase().trim()
        return when (clean) {
            "activewindow", "activewindowv2", "windowtitle", "windowtitlev2" -> "活动窗口已切换"
            "workspace", "workspacev2" -> "工作区已切换"
            "focusedmon" -> "焦点显示器已切换"
            "destroyworkspace" -> "工作区已销毁"
            "createworkspace" -> "工作区已创建"
            "fullscreen" -> "全屏模式已切换"
            "monitoradded", "monitoraddedv2" -> "已连接显示器"
            "monitorremoved" -> "已断开显示器"
            "openwindow" -> "窗口已打开"
            "closewindow" -> "窗口已关闭"
            "movewindow", "movewindowv2" -> "窗口已移动"
            else -> rawEvent
        }
    }
}
