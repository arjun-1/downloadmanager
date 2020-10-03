import cats.Show
package object downloadmanager {
  implicit val showThrowable: Show[Throwable] = Show.fromToString[Throwable]

}
