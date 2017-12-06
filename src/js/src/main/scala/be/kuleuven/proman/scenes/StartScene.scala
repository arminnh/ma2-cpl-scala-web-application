package be.kuleuven.proman.scenes

import be.kuleuven.proman.{errorAlert, formatTimeStamp, hideError, showError}
import be.kuleuven.proman.models._

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global // implicit ExecutionContext for Future tasks
import io.circe.syntax._, io.circe.parser._, io.circe.Json
import cats.syntax.either._
//import io.circe.generic.auto._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.html._ // HTMLDivElement => Div
import org.scalajs.dom.raw.{Event, NodeListOf}
import scalatags.JsDom.all._ // Client side HTML Tags
import scala.scalajs.js.Any

//noinspection AccessorLikeMethodIsUnit
object StartScene {
  lazy val todo_project_ui = new TODOProjectTemplate(scalatags.JsDom)
  var state: Long = -999

  def setupHTML(): Unit = {
    hideError()

    dom.document.title = "Todo Projects"
    dom.document.getElementById("top-title").innerHTML = div(
      h1("Todo Projects", fontSize := 36)
    ).render.innerHTML

    dom.document.getElementById("content").innerHTML = div(
      h2("Create a new project"),
      form(id := "form-create-project", action := "/projects/store", method := "post", cls := "form-inline")(
        div(cls := "form-group")(
          input(tpe := "text", name := "name", placeholder := "Project title", cls := "form-control", autocomplete := "off")
        ),
        button(tpe := "submit", cls := "btn btn-primary", marginLeft := 15)("Create")
      ),
      h2("Open  a project"),
      div(id := "project-container")(
        div(cls := "table-responsive")(
          table(cls := "table table-condensed table-striped table-hover")(tbody)
        )
      )
    ).render.innerHTML

    dom.document.getElementById("form-create-project").asInstanceOf[Form].onsubmit = Any.fromFunction1((e: Event) => {
      e.preventDefault()
      submitNewProject(e.srcElement.asInstanceOf[Form])
    })
  }

  def setupScene(): Unit = {
    setupHTML()
    state = 0
    synchronise()
    // TODO remove interval when moving to new page
    dom.window.setInterval(Any.fromFunction0(() => synchronise()), 2500)
  }

  def addProjectsToTable(projects: Seq[TODOProject]): Unit = {
    val tbody = dom.document.getElementById("project-container").getElementsByTagName("tbody").item(0).asInstanceOf[TableSection]
    val tempRow = tbody.insertRow(0)

    // Insert projects in table and set onclick event handler.
    projects.foreach(project => {
      val row = tbody.insertBefore(todo_project_ui.singleTemplate(project).render, tempRow).asInstanceOf[TableRow]

      val anchor = row.getElementsByClassName("project-anchor").item(0).asInstanceOf[Anchor]
      anchor.onclick = Any.fromFunction1(_ => getProjectAndShow(anchor.getAttribute("data-id").toInt))
    })

    tbody.removeChild(tempRow)
  }

  def getProjectAndShow(id: Int): Unit = {
    Ajax.get("/project/" + id).onComplete {
      case Failure(error) => errorAlert(error)
      case Success(xhr) =>
        val projectM = decode[TODOProject](xhr.responseText)

        projectM match {
          case Left(error) => errorAlert(error)
          case Right(project) => {
            // TODO" check what this does again
//            dom.window.history.pushState("", dom.document.title, dom.window.location.pathname)
            ProjectScene.setupScene(project)
          }
        }
    }
  }

  // Submit a new project if possible and then load it if successful.
  def submitNewProject(form: Form): Unit = {
    hideError()

    val name = form.elements.namedItem("name").asInstanceOf[Input].value
    if (name.length() == 0) {
      showError("Fill in a name first!")
    } else {
      val encodedName = scalajs.js.URIUtils.encodeURIComponent(name)
      Ajax.get("project/exists/" + encodedName).onComplete {
        case Failure(error) => errorAlert(error)
        case Success(xhr) => {
          val existsM = decode[Boolean](xhr.responseText)

          existsM match {
            case Left(error) => errorAlert(error)
            case Right(exists) => {
              if (exists) {
                showError("A project with that name already exists! Try again with a different name.")
              } else {

                //Ajax.post(form.action, name.asJson.toString()).onComplete {
                Ajax.post(form.action, new TODOProject(name).asJson.noSpaces).onComplete {
                  case Failure(error) => errorAlert(error)
                  case Success(xhr2) =>
                    form.reset()
                    val new_projectM = decode[TODOProject](xhr2.responseText)

                    new_projectM match {
                      case Left(error) => errorAlert(error)
                      case Right(new_project) => ProjectScene.setupScene(new_project)
                    }
                }
              }
            }
          }
        }
      }
    }
  }

  def synchronise(): Unit = {
    println("synchronising for state: " + this.state)

    Ajax.get("projects/sync/"+state).onComplete {
      case Failure(error) => errorAlert(error)
      case Success(xhr) =>
        parse(xhr.responseText) match {
          case Left(error) => errorAlert(error)
          case Right(json) =>
            val projects: Seq[TODOProject] = json.hcursor.downField("projects").as[Seq[TODOProject]].getOrElse(List())
            addProjectsToTable(projects)

            this.state = json.hcursor.downField("state").as[Long].getOrElse(this.state)
        }
    }
  }
}
