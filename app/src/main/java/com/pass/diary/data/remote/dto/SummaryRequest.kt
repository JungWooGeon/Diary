package com.pass.diary.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SummaryRequest(
    @SerializedName("document") val document: DocumentObject,
    @SerializedName("option") val option: OptionObject
)

data class DocumentObject(
    @SerializedName("content") val content: String,
    @SerializedName("title") val title: String
)

data class OptionObject(
    @SerializedName("language") val language: String = "ko",
    @SerializedName("tone") val tone: Int = 3,
    @SerializedName("summaryCount") val summaryCount: Int = 1
)