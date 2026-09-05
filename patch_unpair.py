import re

with open('app/src/main/java/com/aeropad/remote/domain/HidController.kt', 'r') as f:
    text = f.read()

text = text.replace("fun bondedDevices(): List<RemoteDevice>", "fun bondedDevices(): List<RemoteDevice>\n    fun unpair(address: String): Boolean")
with open('app/src/main/java/com/aeropad/remote/domain/HidController.kt', 'w') as f:
    f.write(text)

with open('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', 'r') as f:
    text2 = f.read()

unpair_func = """    @android.annotation.SuppressLint("MissingPermission")
    override fun unpair(address: String): Boolean {
        val dev = bondedDeviceByAddress(address) ?: return false
        return runCatching {
            val method = dev.javaClass.getMethod("removeBond")
            method.invoke(dev) as Boolean
        }.onFailure { timber.log.Timber.w(it, "unpair failed for %s", address) }
        .getOrDefault(false)
    }"""

text2 = text2.replace("fun bondedDeviceByAddress(address: String): BluetoothDevice?", unpair_func + "\n\n    fun bondedDeviceByAddress(address: String): BluetoothDevice?")
with open('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', 'w') as f:
    f.write(text2)

with open('app/src/main/java/com/aeropad/remote/domain/usecase/ConnectionUseCases.kt', 'r') as f:
    text3 = f.read()

unpair_usecase = """class UnpairDeviceUseCase @javax.inject.Inject constructor(
    private val controller: com.aeropad.remote.domain.HidController
) {
    operator fun invoke(address: String): Boolean = controller.unpair(address)
}"""

text3 = text3 + "\n" + unpair_usecase + "\n"
with open('app/src/main/java/com/aeropad/remote/domain/usecase/ConnectionUseCases.kt', 'w') as f:
    f.write(text3)

with open('app/src/main/java/com/aeropad/remote/viewmodel/ConnectionViewModel.kt', 'r') as f:
    text4 = f.read()

text4 = text4.replace("private val connectDevice: ConnectDeviceUseCase,", "private val connectDevice: ConnectDeviceUseCase,\n    private val unpairDevice: com.aeropad.remote.domain.usecase.UnpairDeviceUseCase,")
unpair_vm = """    fun unpair(address: String) {
        if (unpairDevice(address)) {
            refreshBondedDevices()
        }
    }"""

text4 = text4.replace("fun disconnect() {\n        disconnectDevice()\n    }", "fun disconnect() {\n        disconnectDevice()\n    }\n\n" + unpair_vm)
with open('app/src/main/java/com/aeropad/remote/viewmodel/ConnectionViewModel.kt', 'w') as f:
    f.write(text4)

