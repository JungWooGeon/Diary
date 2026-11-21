package com.pass.diary.core.ai

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides @Singleton fun assets(@ApplicationContext c: Context): AssetSource = AndroidAssets(c)
    @Provides @Singleton fun crisis(a: AssetSource) = CrisisDetector(a.json("ai/crisis.json")!!.obj)
    @Provides @Singleton fun classifier(a: AssetSource) = EmotionClassifier.load(a)
    @Provides @Singleton fun tags(a: AssetSource) = TagExtractor(a.json("ai/tags.json")!!.obj)
    @Provides @Singleton fun template(a: AssetSource) =
        TemplateReplyEngine(a.json("library/replies.json")!!.arr, a.json("library/questions.json")?.arr)
    @Provides @Singleton fun pipeline(cr: CrisisDetector, cl: EmotionClassifier, tg: TagExtractor, t: TemplateReplyEngine) =
        AiPipeline(cr, cl, tg, t)

    // Interfaces the feature modules depend on
    @Provides fun replyProvider(p: AiPipeline): ReplyProvider = p
    @Provides fun emotionSuggester(p: AiPipeline): EmotionSuggester = p
    @Provides @Singleton fun chat(t: TemplateReplyEngine, cr: CrisisDetector): ChatResponder = OniChat(t, cr)
}
