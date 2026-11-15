package com.wusper.zeyao.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.R
import com.wusper.zeyao.ui.components.PrimaryButton

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Serif,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.biometric_unlock_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton(
            text = stringResource(R.string.biometric_retry),
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
