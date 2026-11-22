package com.pass.diary.core.billing

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumLinkTest {
    @Test fun signedOutNeverCallsAndDropsServerPremium() {
        assertEquals(LinkPlan(LinkAction.NONE, LinkState.SIGNED_OUT, dropServerPremium = true), linkPlan(false, localOwned = false, serverPremium = true, linked = false))
        assertEquals(LinkPlan(LinkAction.NONE, LinkState.SIGNED_OUT), linkPlan(false, localOwned = true, serverPremium = false, linked = true))
    }

    @Test fun localOwnerLinksOnceAndIsNeverDowngraded() {
        assertEquals(LinkPlan(LinkAction.LINK, LinkState.PENDING), linkPlan(true, localOwned = true, serverPremium = false, linked = false))
        assertEquals(LinkPlan(LinkAction.NONE, LinkState.LINKED), linkPlan(true, localOwned = true, serverPremium = false, linked = true))
        // 409 linked_elsewhere / 400 purchase_*: never retried for the same account + purchase
        assertEquals(LinkPlan(LinkAction.NONE, LinkState.ELSEWHERE), linkPlan(true, localOwned = true, serverPremium = false, linked = false, elsewhere = true))
        assertEquals(LinkPlan(LinkAction.NONE, LinkState.LOCAL_ONLY), linkPlan(true, localOwned = true, serverPremium = false, linked = false, rejected = true))
        assertFalse(linkPlan(true, localOwned = true, serverPremium = true, linked = true).dropServerPremium)
    }

    @Test fun nonOwnerAsksTheServer() {
        assertEquals(LinkPlan(LinkAction.CHECK, LinkState.PENDING), linkPlan(true, localOwned = false, serverPremium = false, linked = false))
        assertEquals(LinkPlan(LinkAction.CHECK, LinkState.LINKED), linkPlan(true, localOwned = false, serverPremium = true, linked = true))
    }

    private val seen = mutableListOf<Request>()
    private fun api(code: Int, body: String) = EntitlementApi("https://api.test/", OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        seen += chain.request()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build())

    private fun Request.bodyText() = Buffer().also { body!!.writeTo(it) }.readUtf8()

    @Test fun linkRequestShape() {
        assertEquals(200, api(200, """{"premium":true,"source":"play","since":1}""").link("tok", "premium", "pt-1"))
        val r = seen.single()
        assertEquals("POST", r.method)
        assertEquals("https://api.test/v1/entitlement/play", r.url.toString())
        assertEquals("Bearer tok", r.header("Authorization"))
        assertEquals("""{"productId":"premium","purchaseToken":"pt-1"}""", r.bodyText())
        assertTrue(r.body!!.contentType().toString().startsWith("application/json"))
        assertEquals(409, api(409, """{"error":"linked_elsewhere"}""").link("tok", "premium", "pt-1"))
    }

    @Test fun entitlementAndDeleteShapes() {
        assertEquals(200 to true, api(200, """{"premium":true,"source":"play","since":1}""").entitlement("tok"))
        assertEquals(200 to false, api(200, """{"premium":false,"source":null,"since":null}""").entitlement("tok"))
        assertNull(api(401, """{"error":"unauthorized"}""").entitlement("tok").second)
        assertNull(api(200, "not json").entitlement("tok").second)
        assertEquals(listOf("GET"), seen.map { it.method }.distinct())
        assertEquals("https://api.test/v1/entitlement", seen.first().url.toString())

        seen.clear()
        assertEquals(200, api(200, """{"ok":true}""").deleteMe("tok"))
        assertEquals("DELETE", seen.single().method)
        assertEquals("https://api.test/v1/me", seen.single().url.toString())
        assertEquals("Bearer tok", seen.single().header("Authorization"))
    }
}
