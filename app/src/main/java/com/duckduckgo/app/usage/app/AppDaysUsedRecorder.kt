/*
 * Copyright (c) 2019 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.usage.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class AppDaysUsedRecorder(
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val appCoroutineScope: CoroutineScope,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        appCoroutineScope.launch {
            Timber.i("Recording app used today")
            appDaysUsedRepository.recordAppUsedToday()
        }
    }
}
