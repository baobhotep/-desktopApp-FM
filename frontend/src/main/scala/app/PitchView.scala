package app

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.MouseEvent

/** Wizualizacja boiska z 11 przeciągalnymi slotami. Pozycje w 0–1 (x = własna bramka, y = lewa linia). */
object PitchView {
  val PitchAspectRatio = 105.0 / 68.0

  def render(
    positions: Var[List[(Double, Double)]],
    slotLabels: List[String],
    selectedPlayers: Signal[Map[Int, String]],
    playerNames: Signal[Map[String, String]],
    draggable: Boolean
  ): Element = {
    val dragging = Var[Option[Int]](None)

    def clamp(v: Double): Double = v.max(0.02).min(0.98)

    def handleMouseDown(ev: MouseEvent, idx: Int): Unit =
      if (draggable) {
        ev.preventDefault()
        dragging.set(Some(idx))
      }

    val nodeRefs = scala.collection.mutable.Map.empty[Int, dom.html.Element]

    def handleMouseMove(ev: MouseEvent): Unit =
      if (draggable) {
        dragging.now() match {
          case Some(idx) =>
            val el = ev.currentTarget.asInstanceOf[dom.html.Element]
            val r = el.getBoundingClientRect()
            val x = clamp((ev.clientX - r.left) / r.width)
            val y = clamp((ev.clientY - r.top) / r.height)
            nodeRefs.get(idx).foreach { node =>
              node.style.setProperty("left", s"${x * 100}%")
              node.style.setProperty("top", s"${y * 100}%")
            }
          case _ =>
        }
      }

    def commitDragPosition(): Unit =
      dragging.now().foreach { idx =>
        nodeRefs.get(idx).foreach { node =>
          val left = node.style.getPropertyValue("left").replace("%", "").toDoubleOption.map(_ / 100.0).getOrElse(0.5)
          val top = node.style.getPropertyValue("top").replace("%", "").toDoubleOption.map(_ / 100.0).getOrElse(0.5)
          positions.update { list =>
            val arr = list.padTo(11, (0.5, 0.5)).take(11).toArray
            if (idx >= 0 && idx < arr.length) { arr(idx) = (left, top); arr.toList }
            else list
          }
        }
      }

    div(
      cls := "space-y-2",
      div(
        cls := "relative rounded-lg overflow-hidden border-2 border-green-700 bg-green-800 pitch-aspect",
        tabIndex := 0,
        onMouseMove --> { ev => handleMouseMove(ev) },
        onMouseLeave --> { _ => commitDragPosition(); dragging.set(None) },
        onMouseUp --> { _ => commitDragPosition(); dragging.set(None) },
        onUnmountCallback { _ => nodeRefs.clear() },
        // linie boiska / grafika
        div(cls := "absolute inset-0 border-[1px] border-green-300/60 rounded-lg pointer-events-none"),
        // linia środkowa
        div(cls := "absolute inset-y-0 left-1/2 w-px bg-green-300/70 pointer-events-none"),
        // koło środkowe
        div(cls := "absolute top-1/2 left-1/2 w-24 h-24 -mt-12 -ml-12 rounded-full border border-green-300/70 pointer-events-none"),
        // pola karne
        div(cls := "absolute top-1/4 bottom-1/4 left-0 w-[18%] border border-green-300/70 border-l-0 pointer-events-none"),
        div(cls := "absolute top-1/4 bottom-1/4 right-0 w-[18%] border border-green-300/70 border-r-0 pointer-events-none"),
        // bramki
        div(cls := "absolute top-2/5 bottom-2/5 left-0 w-[4%] border border-green-200/80 border-l-0 bg-green-900/60 pointer-events-none"),
        div(cls := "absolute top-2/5 bottom-2/5 right-0 w-[4%] border border-green-200/80 border-r-0 bg-green-900/60 pointer-events-none"),
        // punkty karne
        div(cls := "absolute left-[12%] top-1/2 w-1 h-1 -mt-0.5 rounded-full bg-green-100 pointer-events-none"),
        div(cls := "absolute right-[12%] top-1/2 w-1 h-1 -mt-0.5 rounded-full bg-green-100 pointer-events-none"),
        children <-- positions.signal.map { posList =>
          val list = posList.padTo(11, (0.5, 0.5)).take(11)
          list.zipWithIndex.map { case ((x, y), idx) =>
            val label = slotLabels.lift(idx).getOrElse(s"S${idx + 1}")
            val leftPct = s"${x * 100}%"
            val topPct = s"${y * 100}%"
            div(
              cls := "absolute w-10 h-10 -ml-5 -mt-5 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs font-medium border-2 border-blue-400 select-none" + (if (draggable) " cursor-grab" else ""),
              onMountCallback { ctx =>
                val el = ctx.thisNode.ref.asInstanceOf[dom.html.Div]
                el.style.setProperty("left", leftPct)
                el.style.setProperty("top", topPct)
                nodeRefs(idx) = el
              },
              role := "button",
              child.text <-- selectedPlayers.combineWith(playerNames).map { case (sel, names) =>
                sel.get(idx).fold(label)(pid => names.getOrElse(pid, pid))
              },
              onMouseDown --> { ev => handleMouseDown(ev, idx) }
            )
          }.toSeq
        }
      ),
      div(
        cls := "flex flex-wrap gap-3 text-[11px] text-green-100/90",
        span(cls := "inline-flex items-center gap-1",
          span(cls := "inline-block w-3 h-3 rounded-full bg-blue-600 border border-blue-300"),
          "Twoi zawodnicy"
        ),
        span(cls := "inline-flex items-center gap-1",
          span(cls := "inline-block w-4 h-px bg-green-300"),
          "Linia środkowa"
        ),
        span(cls := "inline-flex items-center gap-1",
          span(cls := "inline-block w-3 h-3 border border-green-300 rounded-sm"),
          "Pole karne"
        ),
        span(cls := "inline-flex items-center gap-1",
          span(cls := "inline-block w-2 h-2 rounded-full bg-green-100"),
          "Punkt karny"
        )
      )
    )
  }
}
