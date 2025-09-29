package ui

import io.github.yafrl.Event
import io.github.yafrl.EventState
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.runYafrl
import io.github.yafrl.sample
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope
import kotlin.reflect.typeOf

object TextAdventureExample {
    /** Interface for an action that the user can perform that updates that game's state. */
    fun interface Action {
        operator fun invoke(state: GameState): GameState

        data class GoTo(val room: Room) : Action {
            override fun invoke(state: GameState): GameState {
                return state.copy(room = room)
            }
        }
    }

    sealed class Item {
        object Flower : Item()

        object Gold : Item()

        object Flask : Item()
    }

    data class GameState(
        val inventory: Map<Item, Int>,
        val room: Room
    )

    sealed interface Room {
        /** The description that is displayed for each room when first entered, or
         * when the "look" action is performed.*/
        val description: String

        /** List of actions that the user can take to modify the game state. */
        val actions: Map<String, Action>

        object Field : Room {
            override val description = "You find yourself in a vast open field. You can see " +
                    "mountains in the distance on the horizon to the north, and a small farmhouse to your east."

            override val actions: Map<String, Action> = mapOf(
                "pick flower" to Action { it },
                "go east" to Action.GoTo(FarmHouse),
                "go north" to Action.GoTo(Mountain)
            )
        }

        object FarmHouse : Room {
            override val description = "You are in a small rustic farmhouse."

            override val actions = mapOf(
                "go west" to Action.GoTo(Field)
            )
        }

        object Mountain : Room {
            override val description = "You are traversing treacherous terrain through rugged mountain landscapes."
            override val actions: Map<String, Action>
                get() = TODO("Not yet implemented")
        }

        object DragonLair : Room {
            override val description = "You have come across the lair of the dragon."
            override val actions: Map<String, Action>
                get() = TODO("Not yet implemented")
        }
    }

    // TODO: Add example of pluggable 'workflows', e.x. one-off sequences of rooms that can be triggered
    //  by different actions and return a meaningful result that can be used in the rest of the game
    //  e.x. rewards from a dungeon.

    fun TimelineScope.scene(state: GameState): Signal<GameState> = with(state.room) {
        val transitions = Event.merged(
            actions.map { (label, transition: Action) ->
                // Create an event that uses the transition label, and performs
                //  the action specified when fired.
                println("Creating $label")
                externalEvent<Unit>(label).map { transition }
            }
        )

        Signal.fold(state, transitions) { state, action -> action(state) }
    }

    // This example shows that any yafrl program can be investigated and debugged via the terminal
    //  if desired.
    @OptIn(FragileYafrlAPI::class)
    fun playGame() = runYafrl {
        val game = scene(
            GameState(
                inventory = mapOf(),
                room = Room.Field
            )
        )

        val room = game
            .map { it.room }

        sample {
            var currentRoom = room.currentValue()

            while (true) {
                currentRoom = room.currentValue()

                println(currentRoom.description)

                println("What would you like to do?")

                print("> ")
                val response = readln()

                // TODO: Get a list of all active nodes.
                val nodes = timeline.externalNodes

                for (node in nodes) {
                    val label = node.value.node.toString()

                    if (label == response && node.value.type == typeOf<EventState<Unit>>()) {
                        timeline.updateNodeValue(node.value.node, EventState.Fired(Unit))
                        break
                    }
                }

                println("You cannot $response")
            }
        }
    }
}

fun playGame(args: Array<String>) {
    Timeline.initializeTimeline()

    TextAdventureExample.playGame()
}
