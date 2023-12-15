package com.pass.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SummaryResponse(
    @SerializedName("summary") val summary: String,
    @SerializedName("status") val status: String?,
    @SerializedName("error") val error: ErrorBody?
)

data class ErrorBody(
    @SerializedName("errorCode") val errorCode: String?,
    @SerializedName("message") val message: String?
)