/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.ui.notification.*
import com.duckduckgo.vpn.di.VpnCoroutineScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This receiver allows to trigger appTP notifications, to do so, in the command line:
 *
 * $ adb shell am broadcast -a notify --es <weekly/daily> <N>
 *
 * where `--es weekly <N>` will trigger the N'th variant of the weekly notification
 * where `--es daily <N>` will trigger the N'th variant of the daily notification
 */
class DeviceShieldNotificationsDebugReceiver(
    context: Context,
    intentAction: String = "notify",
    private val receiver: (Intent) -> Unit,
) : BroadcastReceiver() {

    init {
        context.registerReceiver(this, IntentFilter(intentAction))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        receiver(intent)
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class,
)
class DeviceShieldNotificationsDebugReceiverRegister @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val deviceShieldNotificationFactory: DeviceShieldNotificationFactory,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val weeklyNotificationPressedHandler: WeeklyNotificationPressedHandler,
    private val dailyNotificationPressedHandler: DailyNotificationPressedHandler,
    private val deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder,
    @VpnCoroutineScope private val vpnCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        if (!appBuildConfig.isDebug) {
            Timber.i("Will not register DeviceShieldNotificationsDebugReceiver, not in DEBUG mode")
            return
        }

        Timber.i("Debug receiver DeviceShieldNotificationsDebugReceiver registered")

        DeviceShieldNotificationsDebugReceiver(context) { intent ->
            val weekly = kotlin.runCatching { intent.getStringExtra("weekly")?.toInt() }.getOrNull()
            val daily = kotlin.runCatching { intent.getStringExtra("daily")?.toInt() }.getOrNull()

            vpnCoroutineScope.launch(dispatchers.io()) {
                val notification = if (weekly != null) {
                    Timber.v("Debug - Sending weekly notification $weekly")
                    weeklyNotificationPressedHandler.notificationVariant = weekly
                    val deviceShieldNotification =
                        deviceShieldNotificationFactory.weeklyNotificationFactory.createWeeklyDeviceShieldNotification(weekly)

                    deviceShieldAlertNotificationBuilder.buildStatusNotification(
                        context,
                        deviceShieldNotification,
                        weeklyNotificationPressedHandler,
                    )
                } else if (daily != null) {
                    Timber.v("Debug - Sending daily notification $daily")
                    dailyNotificationPressedHandler.notificationVariant = daily
                    val deviceShieldNotification = deviceShieldNotificationFactory.dailyNotificationFactory.createDailyDeviceShieldNotification(daily)

                    deviceShieldAlertNotificationBuilder.buildStatusNotification(
                        context,
                        deviceShieldNotification,
                        dailyNotificationPressedHandler,
                    )
                } else {
                    Timber.v("Debug - invalid notification type")
                    null
                }

                notification?.let {
                    notificationManagerCompat.notify(DeviceShieldNotificationScheduler.VPN_WEEKLY_NOTIFICATION_ID, it)
                }
            }
        }
    }
}
