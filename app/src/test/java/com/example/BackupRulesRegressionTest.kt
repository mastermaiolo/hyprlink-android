// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesRegressionTest {

    private fun findXmlFile(filename: String): File {
        val candidates = listOf(
            File("src/main/res/xml/$filename"),
            File("app/src/main/res/xml/$filename"),
            File("../app/src/main/res/xml/$filename")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Ficheiro XML $filename não encontrado nas localizações esperadas: $candidates")
    }

    private data class ExcludeRule(val domain: String, val path: String)

    private fun parseExcludes(element: Element): Set<ExcludeRule> {
        val rules = mutableSetOf<ExcludeRule>()
        val nodeList: NodeList = element.getElementsByTagName("exclude")
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node is Element) {
                val domain = node.getAttribute("domain")
                val path = node.getAttribute("path")
                rules.add(ExcludeRule(domain, path))
            }
        }
        return rules
    }

    private val requiredExcludes = setOf(
        ExcludeRule("sharedpref", "hyprlink_secure_prefs.xml"),
        ExcludeRule("sharedpref", "hyprlink_secure_prefs_fallback.xml"),
        ExcludeRule("sharedpref", "__androidx_security_crypto_encrypted_prefs__.xml"),
        ExcludeRule("database", "hyprlink_database"),
        ExcludeRule("database", "hyprlink_database-wal"),
        ExcludeRule("database", "hyprlink_database-shm"),
        ExcludeRule("file", "diagnostics.log")
    )

    @Test
    fun testBackupRulesXml_containsAllSensitiveExclusions() {
        val file = findXmlFile("backup_rules.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement

        val excludes = parseExcludes(root)

        for (required in requiredExcludes) {
            assertTrue(
                "backup_rules.xml deve excluir domain='${required.domain}' path='${required.path}'",
                excludes.contains(required)
            )
        }
    }

    @Test
    fun testDataExtractionRulesXml_containsAllSensitiveExclusionsInCloudAndTransfer() {
        val file = findXmlFile("data_extraction_rules.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement

        val cloudBackupNodes = root.getElementsByTagName("cloud-backup")
        assertTrue("data_extraction_rules.xml deve conter tag <cloud-backup>", cloudBackupNodes.length > 0)
        val cloudExcludes = parseExcludes(cloudBackupNodes.item(0) as Element)

        val deviceTransferNodes = root.getElementsByTagName("device-transfer")
        assertTrue("data_extraction_rules.xml deve conter tag <device-transfer>", deviceTransferNodes.length > 0)
        val deviceExcludes = parseExcludes(deviceTransferNodes.item(0) as Element)

        for (required in requiredExcludes) {
            assertTrue(
                "<cloud-backup> deve excluir domain='${required.domain}' path='${required.path}'",
                cloudExcludes.contains(required)
            )
            assertTrue(
                "<device-transfer> deve excluir domain='${required.domain}' path='${required.path}'",
                deviceExcludes.contains(required)
            )
        }
    }
}
