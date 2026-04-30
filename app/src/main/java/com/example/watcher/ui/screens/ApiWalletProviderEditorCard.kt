package com.example.watcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.watcher.ui.viewmodel.ApiWalletDraft

@Composable
internal fun ProviderEditorCard(
    draft: ApiWalletDraft,
    isSaving: Boolean,
    onDraftChange: ((ApiWalletDraft) -> ApiWalletDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (draft.id == null) "新增供应商" else "编辑供应商",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "保存后，这个钱包条目就会对应用其他模块可用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> onDraftChange { it.copy(name = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("供应商名称") },
                singleLine = true
            )
            OutlinedTextField(
                value = draft.endpoint,
                onValueChange = { value -> onDraftChange { it.copy(endpoint = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("接口地址") },
                singleLine = true
            )
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { value -> onDraftChange { it.copy(apiKey = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 密钥") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            OutlinedTextField(
                value = draft.modelName,
                onValueChange = { value -> onDraftChange { it.copy(modelName = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名称") },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("启用", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "禁用后仍会保留该供应商，但不会被自动选中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { checked -> onDraftChange { it.copy(enabled = checked) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (isSaving) "保存中..." else "保存")
                }
            }
        }
    }
}
