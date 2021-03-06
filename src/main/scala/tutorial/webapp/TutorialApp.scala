package tutorial.webapp

import lib._
import org.scalajs.dom
import org.scalajs.dom.html.{Element}
import rx.core.{Var, Rx}

import scala.scalajs.js.{JSApp}

import scala.util.{Failure, Success}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scalatags.JsDom.tags2.section

import scalatags.JsDom.all._

object TutorialApp extends JSApp {
  import Framework._

  val repositories: Var[Seq[Repository]] = Var(Seq[Repository]())

  val userInputBox = input(
    `id`:="userInputBox",
    `class`:= "form-control",
    autofocus:=true,
    autocomplete:=false,
    placeholder := "user name here.",
    value:="YusukeKokubo"
  ).render

  val userSubmit = button(
    `type`:="submit",
    `class`:="btn btn-default",
    onclick:={ () =>
      getRepositories(Var(userInputBox.value)())
      false
    })("send").render

  val errorMessage = Var("")

  case class Debug(url: String, res: String)

  val debug = Var(List[Debug]())

  def main(): Unit = {
    dom.document.getElementById("error").appendChild(showError())
    dom.document.getElementById("repositories").appendChild(setupUI())
    dom.document.getElementById("debug").appendChild(setupDebug())
    GitHub.hook = (url: String, res: String) => {
      debug() = Debug(url, res) :: debug()
    }
  }

  def setupUI(): Element = {
    section(
      form(`class`:="form-inline")(div(`class`:="form-group", i(`class`:="fa fa-github-alt fa-3"), userInputBox), userSubmit),
      Rx {
        ul(
          repositories().map { showRepository }
        )
      }
    ).render
  }

  def setupDebug(): Element = {
    div(`class`:="panel-group", role:="tablist", id:="accordion", aria.multiselectable:=true)(
      Rx {
        debug().zipWithIndex.map { case(d, i) =>
          div(`class` := "panel panel-default")(
            div(`class` := "panel-heading", role := "tab", id:="hedding" + i)(
              h4(`class` := "panel-title")(
                a(data.toggle := "collapse", data.parent := "#accordion", aria.expanded := false, aria.controls := "collapse" + i, href:="#collapse" + i)(d.url)
              )
            ),
            div(`class`:="panel-collapse collapse " + (if(i == 0) "in" else ""), role:="tabpanel", aria.labelledby:="hedding" + i, id:="collapse" + i, aria.expanded:=false)(
              div(`class`:="panel-body")(pre(d.res))
            )
          )
        }
      }
    ).render
  }

  def showError(): Element = {
    div(`class`:="error")(
      Rx {
        p(errorMessage())
      }
    ).render
  }

  def showRepository(r: Repository): Element = {
    val refs = Var(Seq[Reference]())
    li(referenceAnchor(r, refs),
      Rx {
        ul(
          refs().map { showReference(r, _) }
        )
      }
    ).render
  }

  def showReference(r: Repository, ref: Reference): Element = {
    val commits = Var(List[Commit]())
    li(commitAnchor(r, ref.`object`.sha, ref.ref, commits),
      Rx {
        ul(
          commits().map { showCommit(r, _) },
          for (p <- if (!commits().isEmpty) commits().reverse.head.parents else Seq()) yield {
            li(commitAnchor(r, p.sha, p.sha.substring(0, 6), commits))
          }
        )
      }
    ).render
  }

  def showCommit(repo: Repository, commit: Commit): Element = {
    val trees = Var[Option[Trees]](None)
    li(`class`:="commit")(
      label(commit.author.date),
      label(commit.author.name),
      label(commit.message),
      a(href:="#")(span(`class`:="glyphicon glyphicon-plus", aria.hidden:=true))(onclick:={() =>
        getTrees(Var(userInputBox.value)(), repo.name, commit.sha, trees)
      }),
      Rx {
        ul(
          trees() match {
            case Some(ts) =>
              ts.tree.map{t =>
                if (t.`type` == "tree") {
                  li(t.path)(span(`class`:="glyphicon glyphicon-tree-deciduous"))
                } else {
                  li(a(t.path)(onclick:={() =>
                    getBlob(Var(userInputBox.value)(), repo.name, t.sha)
                  }))
                }
              }
            case None => {}
          }
        )
      }
    ).render
  }

  def commitAnchor(repo: Repository, sha: String, caption: String, commits: Var[List[Commit]]): Element = {
    a(href:="#")(onclick:={() =>
      getCommit(Var(userInputBox.value)(), repo.name, sha, commits)
      false
    })(caption).render
  }

  def referenceAnchor(repo: Repository, refs: Var[Seq[Reference]]): Element = {
    a(href:="#")(onclick:={() =>
      getReferences(Var(userInputBox.value)(), repo.name, refs)
      false
    })(repo.name).render
  }

  def getRepositories(user: String): Unit = {
    if(user.isEmpty) {
      errorMessage() = "input user name."
      return
    }
    GitHub.repos(user).onComplete {
      case Success(msg) => repositories() = msg
      case Failure(t) => errorMessage() = t.getMessage
    }
  }

  def getReferences(owner: String, repo: String, result: Var[Seq[Reference]]): Unit = {
    GitHub.refs(owner, repo).onComplete {
      case Success(msg) => result() = msg
      case Failure(t) => errorMessage() = t.getMessage
    }
  }

  def getCommit(owner: String, repo: String, sha: String, result: Var[List[Commit]]): Unit = {
    GitHub.commit(owner, repo, sha).onComplete {
      case Success(msg) => result() = result() :+ msg
      case Failure(t) => errorMessage() = t.getMessage
    }
  }

  def getTrees(owner: String, repo: String, sha: String, result: Var[Option[Trees]]): Unit = {
    GitHub.trees(owner, repo, sha).onComplete {
      case Success(msg) => result() = Some(msg)
      case Failure(t) => errorMessage() = t.getMessage
    }
  }

  def getBlob(owner: String, repo: String, sha: String): Unit = {
    GitHub.blob(owner, repo, sha).onComplete {
      case Success(msg) => {}
      case Failure(t) => errorMessage() = t.getMessage
    }
  }
}
