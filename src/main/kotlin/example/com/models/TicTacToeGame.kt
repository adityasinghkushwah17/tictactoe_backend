package example.com.models

import ch.qos.logback.core.joran.spi.EventPlayer
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class TicTacToeGame {
    private val state = MutableStateFlow(GameState())
    private val playersockets =
        ConcurrentHashMap<Char, WebSocketSession>() // this will have the socket of a player
    private val gamescope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var delayGameJob: Job? = null

    init {
        state.onEach { ::broadcast }.launchIn(gamescope)
    }

    fun ConnectPlayer(session: WebSocketSession): Char? {
        val isPlayerX = state.value.connectedPlayers.any { it == 'X' }
        val player = if (isPlayerX) 'O' else 'X'
        state.update {
            if (state.value.connectedPlayers.contains(player)) {
                return null
            }
            if (!playersockets.containsKey(player)) { //here we are checking that the player is not already connected
                playersockets[player] = session

            }
            it.copy(connectedPlayers = it.connectedPlayers + player)
        }
        return player

    }

    fun DisconnectPlayer(player: Char) {
        playersockets.remove(player)
        state.update {
            it.copy(connectedPlayers = it.connectedPlayers - player)
        }
    }

    suspend fun broadcast(state: GameState) {
        playersockets.values.forEach { socket ->
            socket.send(Json.encodeToString(state))
        }

    }

    fun finishTurn(player: Char, x: Int, y: Int) {
        if (state.value.field[y][x] != null || state.value.winningPlayer != null) {
            return
        }
        if (state.value.playerAtTurn != player) {
            return
        }
        val currentPlayer = state.value.playerAtTurn
        state.update {
            val newField = it.field.also { field ->
                field[y][x] = currentPlayer
            }
            val isBoardFull = newField.all { it.all { it != null } }
            if (isBoardFull) {
                startNewRoundDelay()
            }
            it.copy(
                playerAtTurn = if (currentPlayer == 'X') 'Y' else 'X',
                field = newField,
                isBoardFull = isBoardFull,
                winningPlayer = getWinningPlayer()?.also {
                    startNewRoundDelay()
                }
            )
        }
    }

    private fun getWinningPlayer(): Char? {
        val field = state.value.field
        return if (field[0][0] != null && field[0][0] == field[0][1] && field[0][1] == field[0][2]) {
            field[0][0]
        } else if (field[1][0] != null && field[1][0] == field[1][1] && field[1][1] == field[1][2]) {
            field[1][0]
        } else if (field[2][0] != null && field[2][0] == field[2][1] && field[2][1] == field[2][2]) {
            field[2][0]
        } else if (field[0][0] != null && field[0][0] == field[1][0] && field[1][0] == field[2][0]) {
            field[0][0]
        } else if (field[0][1] != null && field[0][1] == field[1][1] && field[1][1] == field[2][1]) {
            field[0][1]
        } else if (field[0][2] != null && field[0][2] == field[1][2] && field[1][2] == field[2][2]) {
            field[0][2]
        } else if (field[0][0] != null && field[0][0] == field[1][1] && field[1][1] == field[2][2]) {
            field[0][0]
        } else if (field[0][2] != null && field[0][2] == field[1][1] && field[1][1] == field[2][0]) {
            field[0][2]
        } else null

    }

    private fun startNewRoundDelay() {
        delayGameJob?.cancel()
        delayGameJob = gamescope.launch {
            delay(5000)
            state.update {
                it.copy(
                    playerAtTurn = 'X',
                    field = GameState.emptyfield(),
                    winningPlayer = null,
                    isBoardFull = false

                )
            }
        }

    }
}