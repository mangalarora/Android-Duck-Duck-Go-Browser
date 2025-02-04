/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.tabs.db

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@SingleInstanceIn(AppScope::class)
class TabsDbSanitizer @Inject constructor(
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        runBlocking {
            launch { purgeTabsDatabaseAsync() }
        }
    }

    private suspend fun purgeTabsDatabaseAsync() = withContext(dispatchers.io()) {
        tabRepository.purgeDeletableTabs()
    }
}
