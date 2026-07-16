package jp.awabi2048.cccontent.features.party

class PartyFeature @JvmOverloads constructor(
    configuration: PartyConfiguration = PartyConfiguration(),
    eventSink: PartyEventSink = NoopPartyEventSink,
    nowMillis: () -> Long = System::currentTimeMillis,
    store: PartyStore? = null
) : AutoCloseable {
    val service = PartyService(configuration, eventSink, nowMillis, store)
    val isEnabled: Boolean get() = service.isEnabled()
    override fun close() = service.close()
    fun disable() = service.disable()
}
