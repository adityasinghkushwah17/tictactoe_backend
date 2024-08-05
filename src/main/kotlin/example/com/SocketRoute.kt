package example.com

import example.com.models.MakeTurn
import example.com.models.TicTacToeGame
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun Route.socket(game: TicTacToeGame) {
    route("/play") {
        webSocket {
            val player = game.ConnectPlayer(this)

            if (player == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "2 players already connected"))
                return@webSocket

            }

            try {
                incoming.consumeEach { frame->
                    if(frame is Frame.Text){
                        val action = ExtractAction(frame.readText())
                        game.finishTurn(player,action.x,action.y)
                    }

                }

            }catch (e:Exception){
                e.printStackTrace()
            }finally {
               game.DisconnectPlayer(player)
            }
        }
    }
}

private fun ExtractAction(message: String):MakeTurn{
    //maketurn#{...}
    val type =  message.substringBefore("#")
    val body =  message.substringAfter("#")
    return if(type=="make_turn"){
        Json.decodeFromString(body)
    }else{
        MakeTurn(-1,-1)
    }

}