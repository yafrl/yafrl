package ui

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.signal
import io.github.yafrl.testing.testPropositionHoldsFor
import io.github.yafrl.vector.Float2
import ui.PolygonExample.delta
import ui.PolygonExample.distance
import kotlin.math.pow
import kotlin.math.sqrt

class PolygonTest {


   @OptIn(FragileYafrlAPI::class)
   // @Test // Disabled -- test is currently broken.
   fun polygonSpec() = testPropositionHoldsFor(
      setupState = {
         val viewModel = PolygonExample.ViewModel(timeline)

         signal {
            PolygonState(
               viewModel.polygons.bind(),
               viewModel.vertices.bind(),
               viewModel.mousePosition.bind(),
               viewModel.clicks.asSignal().bind().isFired(),
               viewModel.createPolygonButton.asSignal().bind().isFired(),
               viewModel.editingPolygon.bind().isNone()
            )
         }
      },
      proposition = {


         val initialCondition by condition {
            // start out _not_ editing
            current.vertices.isEmpty() &&
                    // and with no polygons drawn yet
                    current.polygons.isEmpty() &&
                    // “New Polygon” button is available
                    current.newPolygonButtonEnabled &&
                    // nothing queued for “edit”
                    !current.newPolygonButtonClicked &&
                    // no stray mouse clicks hanging over
                    !current.mouseClicked
         }

         // -- STATE PREDICATES --------------------------------------------------

         // "we're in polygon‐edit mode iff we have at least one handle"
         val editing by condition { current.vertices.isNotEmpty() }

         // explicit idle/editing states for clarity
         val stateIdle    = !editing
         val stateEditing = editing

         // -- TRANSITION PREDICATES ---------------------------------------------
         // these fire at the step where the transition happens...

         // 1) start a new polygon
         val startPolygon by condition {
            current.newPolygonButtonClicked &&
                    current.newPolygonButtonEnabled &&
                    next(editing).holds()  // next we should be in edit mode
         }

         // 2) click to add a new vertex (not closing)
         val addVertex by condition {
            stateEditing.holds() &&
                    current.mouseClicked &&
                    // distance to *last* vertex > delta, so it’s a true “new” point
                    distance(current.mousePosition, current.vertices.last().position) > delta &&
                    // next we see one more handle in the list
                    next.vertices.size == current.vertices.size + 1
         }

         // 3) click to close polygon (within delta of first point)
         val closePolygon by condition {
            stateEditing.holds() &&
                    current.mouseClicked &&
                    distance(current.mousePosition, current.vertices.first().position) <= delta &&
                    // next the polygon is added...
                    next.polygons.size == current.polygons.size + 1 &&
                    // ...and we’ve exited edit mode
                    next(!editing).holds()
         }

         // 4) long‐press drag on an existing handle
         val dragVertex by condition {
            stateEditing.holds() &&
                    current.mouseClicked &&
                    current.vertices.any { it.position == current.mousePosition } &&
                    // next we see *some* handle at a new position
                    next.vertices.indices.any { i ->
                       next.vertices[i].position != previous?.vertices[i]?.position
                    }
         }

         // -- Full specification -----------------------------------------------
         initialCondition and always(
            (startPolygon releases stateEditing) or
                    (addVertex    releases stateEditing) or
                    (dragVertex   releases stateEditing) or
                    (closePolygon releases stateIdle)
         )
      }
   )

   data class PolygonState(
      val polygons: List<PolygonExample.Polygon>,
      val vertices: List<PolygonExample.VertexHandle>,
      val mousePosition: Float2,
      val mouseClicked: Boolean,
      val newPolygonButtonClicked: Boolean,
      val newPolygonButtonEnabled: Boolean,
   )
}