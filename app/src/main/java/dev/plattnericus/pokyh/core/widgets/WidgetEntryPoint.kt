package dev.plattnericus.pokyh.core.widgets

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** [GlanceAppWidget][androidx.glance.appwidget.GlanceAppWidget] instances are constructed by
 * Glance itself (no-arg), not by Hilt, so they can't take a `@Inject` constructor — this is the
 * standard `EntryPointAccessors.fromApplication(...)` bridge into the Hilt graph instead. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetDataBridge(): WidgetDataBridge
}
