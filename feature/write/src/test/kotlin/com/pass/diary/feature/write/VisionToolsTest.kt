package com.pass.diary.feature.write

import com.pass.diary.core.model.Photo
import com.pass.diary.core.vision.FakeHandwriting
import com.pass.diary.core.vision.FakeVision
import com.pass.diary.core.vision.ImageLabel
import com.pass.diary.core.vision.Readiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VisionToolsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val photo = Photo(id = "p1", entryId = "e", localPath = "/x/p1.jpg", w = 1, h = 1)

    @Test fun photoLabelsBecomeChipsOnlyWhenTheModuleIsReady() {
        val vision = FakeVision(ready = Readiness.PREPARING, labels = listOf(ImageLabel("Dog", 0.9f)))
        val vm = VisionViewModel(vision, FakeHandwriting(), RuntimeEnvironment.getApplication())
        vm.labelPhotos(listOf(photo))
        assertEquals(emptyList<String>(), vm.state.value.photoTags.map { it.value }) // still downloading: nothing, no crash

        vision.ready = Readiness.READY
        vm.labelPhotos(listOf(photo))
        assertEquals(listOf("강아지"), vm.state.value.photoTags.map { it.value })

        vm.labelPhotos(emptyList()) // photo removed -> its chips go too
        assertEquals(emptyList<String>(), vm.state.value.photoTags)
    }
}
