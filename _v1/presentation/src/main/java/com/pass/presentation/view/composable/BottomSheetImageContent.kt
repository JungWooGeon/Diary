package com.pass.presentation.view.composable

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BottomSheetImageContent(
    isAdd: Boolean,
    onSelectImageUri: (Uri) -> Unit
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onSelectImageUri(it) }
    }

    // 권한 허용 - OS 버전에 따른 파일/미디어 읽기 권한 목록
    val recordPermissionsState = rememberMultiplePermissionsState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(READ_MEDIA_IMAGES)
        } else {
            listOf(READ_EXTERNAL_STORAGE)
        }
    ) { permissions ->
        if (permissions.all { it.value }) {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val selectImageWithRequestPermission: () -> Unit = {
        val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            recordPermissionsState.permissions.map { it.status.isGranted }.any { it }
        } else {
            recordPermissionsState.allPermissionsGranted
        }

        if (permissionGranted) {
            // 권한 허용 - 사진 선택
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else if (recordPermissionsState.shouldShowRationale) {
            // 권한 미허용 - 설정으로 이동
            coroutineScope.launch {
                Toast.makeText(context, "이미지 기능을 사용하려면 파일/미디어 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }
        } else {
            // 처음일 경우 - 권한 요청
            recordPermissionsState.launchMultiplePermissionRequest()
        }
    }

    if (isAdd) {
        ListItem(
            headlineContent = { Text(text = "이미지 추가") },
            leadingContent = {
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = "이미지 추가",
                )
            },
            modifier = Modifier.clickable { selectImageWithRequestPermission() }
        )
    } else {
        ListItem(
            headlineContent = { Text(text = "이미지 수정") },
            leadingContent = {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "이미지 수정",
                )
            },
            modifier = Modifier.clickable { selectImageWithRequestPermission() }
        )

        ListItem(
            headlineContent = { Text(text = "이미지 삭제") },
            leadingContent = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "이미지 삭제",
                )
            }
        )
    }
}