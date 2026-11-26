package sk.vladko.pill.engine

import sk.vladko.pill.features.Alarm
import sk.vladko.pill.features.AlarmSource
import sk.vladko.pill.features.AlarmView
import sk.vladko.pill.features.Call
import sk.vladko.pill.features.CallSource
import sk.vladko.pill.features.CallView
import sk.vladko.pill.features.Charger
import sk.vladko.pill.features.ChargerSource
import sk.vladko.pill.features.ChargerView
import sk.vladko.pill.features.Message
import sk.vladko.pill.features.MessageSource
import sk.vladko.pill.features.MessageView
import sk.vladko.pill.features.Music
import sk.vladko.pill.features.MusicSource
import sk.vladko.pill.features.MusicView
import sk.vladko.pill.features.Navigation
import sk.vladko.pill.features.NavigationSource
import sk.vladko.pill.features.NavigationView
import sk.vladko.pill.rules.PillItem

/** Every event and activity type. A new type is one new line here. Order matters only for notifications: the first source that claims one keeps it. */
object Registry {
    val features: List<Feature<*>> =
        listOf(
            Feature(Call::class, CallSource, CallView),
            Feature(Alarm::class, AlarmSource, AlarmView),
            Feature(Navigation::class, NavigationSource, NavigationView),
            Feature(Message::class, MessageSource, MessageView),
            Feature(Charger::class, ChargerSource, ChargerView),
            Feature(Music::class, MusicSource, MusicView),
        )

    fun of(item: PillItem): Feature<*> = features.first { it.type.isInstance(item) }
}
