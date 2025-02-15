package dispatch.github

import dispatch._
import dispatch.Request._
import dispatch.liftjson.Js._
import net.liftweb.json._

/** Client is a function to wrap API operations */
abstract class AbstractClient extends ((Request => Request) => Request) {
  def hostname = "api.github.com"
  def host: Request
  def handle[T](method: Method[T]) =
    method.default_handler(apply(method))
}

case object Client extends AbstractClient {
  val host = :/(hostname).secure
  def apply(block: Request => Request): Request = block(host)
}

object OAuth {
  val authorizations = :/("api.github.com").secure / "authorizations"
  /** Fetches a new access token given a gh username and password
   *  and optional list of scopes */
  def accessToken(user: String, pass: String, scopes: Seq[String] = Nil): Option[String] = {
    val client = new Http with NoLogging
    try {
      client(authorizations.POST.as_!(user, pass) << """{"scopes":[%s]}""".format(
        scopes.mkString("\"","\",","\"")
      ) ># { _ \ "token" match {
        case JString(tok) => Some(tok)
        case _ => None
      }})
    } finally {
      client.shutdown()
    }
  }
}

case class OAuthClient(token: String) extends AbstractClient {
  val host = :/(hostname).secure
  def apply(block: Request => Request): Request = block(host) <<? Map("access_token" -> token)
}

trait MethodBuilder extends Builder[Request => Request] {
  final def product = setup andThen complete
  def setup = identity[Request] _
  def complete: Request => Request
}

trait Method[T] extends MethodBuilder {
  /** default handler used by AbstractClient#handle. You can also apply the client
      to a Method and define your own request handler. */
  def default_handler: Request => Handler[T]
}

trait ResourceMethod extends Method[JValue] {
  def default_handler = _ ># identity[JValue]
}

// trait QueryMethod extends Method[(List[JValue],List[JValue])] {
//   def default_handler = _ ># (Response.results ~ Response.meta)
// }
