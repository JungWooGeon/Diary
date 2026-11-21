package com.pass.diary.core.billing

import kotlinx.coroutines.flow.Flow

/** Whether the one-time "프리미엄" (Play in-app product `premium`) is owned. Cached, so it is right offline too. */
interface PremiumState {
    val isPremium: Flow<Boolean>
}
