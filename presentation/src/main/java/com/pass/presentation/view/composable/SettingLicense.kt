package com.pass.presentation.view.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pass.presentation.view.screen.Constants

@Composable
fun SettingLicense(
    onClickUrl: (url: String) -> Unit
) {
    LazyColumn {
        itemsIndexed(Constants.OPENSOURCE_LICENSE_LIST) { _, licenseArray ->
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Text(text = licenseArray[0])
                Text(
                    text = licenseArray[1],
                    modifier = Modifier.clickable { onClickUrl(licenseArray[1]) }
                )
            }
        }
    }
}