with open('app/src/main/java/com/aeropad/remote/viewmodel/GamepadBuilderViewModel.kt', 'r') as f:
    text = f.read()

init_pattern = """    init {
        viewModelScope.launch { repository.seedIfEmpty() }
    }"""
new_init = """    init {
        viewModelScope.launch { repository.seedIfEmpty() }
        observeConnection().onEach { state ->
            if (!state.isConnected) {
                hidState = GamepadSnapshot()
            }
        }.launchIn(viewModelScope)
    }"""
text = text.replace(init_pattern, new_init)

with open('app/src/main/java/com/aeropad/remote/viewmodel/GamepadBuilderViewModel.kt', 'w') as f:
    f.write(text)
