package com.zeticai.zetic_mlange_android;

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zeticai.mlange.core.benchmark.BenchmarkModel
import com.zeticai.mlange.core.model.ZeticMLangeModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZeticMLangeModelTest {

    @get:Rule
    var activityRule: ActivityScenarioRule<MainActivity> = ActivityScenarioRule(
        MainActivity::class.java
    )

    @Test
    @Throws(Exception::class)
    fun test_create_model() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

    }
}