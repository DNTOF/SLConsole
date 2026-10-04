// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dntof.slconsole.data.local.SignatureCheck

/** 点徽标或「非官方版本」时弹出：当前安装包证书指纹和官方指纹，方便用户自己核对。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureSheet(report: SignatureCheck.Report, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val official = report.mismatch == 0
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("签名证书", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (official) {
                    "当前安装包的签名与官方证书一致。"
                } else {
                    "当前安装包的签名与官方证书不一致，可能被修改或重新打包。请从 ${SignatureCheck.RELEASES_URL} 下载官方版本。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (official) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Fingerprint("当前安装包（PackageManager）", SignatureCheck.hex(report.packageManagerHash))
            Fingerprint("当前安装包（APK 签名块）", SignatureCheck.hex(report.apkBlockHash))
            Fingerprint("官方证书", SignatureCheck.hex(report.official))
            Text(
                "SHA-256。官方指纹也写在 GitHub 仓库的 README 里，可以对照核对。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Fingerprint(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        // 32 字节分两行，每行 16 个，免得一个字节被折到下一行
        val parts = value.split(":")
        val shown = if (parts.size == 32) {
            parts.take(16).joinToString(":") + "\n" + parts.drop(16).joinToString(":")
        } else {
            value
        }
        SelectionContainer {
            Text(
                shown,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
