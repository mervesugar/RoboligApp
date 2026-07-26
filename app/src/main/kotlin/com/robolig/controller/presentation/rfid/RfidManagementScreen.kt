@file:Suppress("FunctionName")

package com.robolig.controller.presentation.rfid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.robolig.controller.domain.model.City

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun RfidManagementScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RfidManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RFID Yönetimi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            City.entries.forEach { city ->
                val uid = uiState.cityUids[city] ?: ""
                val error = uiState.cityErrors[city]

                OutlinedTextField(
                    value = uid,
                    onValueChange = { viewModel.onUidChanged(city, it) },
                    label = { Text(city.displayName) },
                    isError = error != null,
                    supportingText = {
                        if (error != null) {
                            Text(error)
                        } else {
                            Text("4, 7 veya 10 bayt UID (Hex formatında)")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            if (uiState.saveStatusMessage != null) {
                Text(text = uiState.saveStatusMessage!!)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.clearFields() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Alanları Temizle")
                }

                Button(
                    onClick = { viewModel.saveToTablet() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Tablete Kaydet")
                }
            }

            Button(
                onClick = { },
                enabled = uiState.isSendToRobotEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Robota Gönder")
            }
        }
    }
}
