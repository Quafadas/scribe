package scribe.json

import fabric._
import fabric.rw._
import perfolation._
import scribe.LogRecord
import scribe.message.Message
import scribe.output.format.OutputFormat
import scribe.output.{LogOutput, TextOutput}
import scribe.writer.Writer

case class JsonWriter(writer: Writer, compact: Boolean = true) extends Writer {
  override def write(record: LogRecord, output: LogOutput, outputFormat: OutputFormat): Unit = {
    val l = record.timeStamp
    val traces = record.messages.collect {
      case message: Message[_] if message.value.isInstanceOf[Throwable] => throwable2Trace(message.value.asInstanceOf[Throwable])
    } match {
      case Nil => Null
      case t :: Nil => t.toValue
      case list => list.toValue
    }
    val messages = record.messages.collect {
      case message: Message[_] if !message.value.isInstanceOf[Throwable] => message.logOutput.plainText
    } match {
      case Nil => Null
      case m :: Nil => m.toValue
      case list => list.toValue
    }
    val r = Record(
      level = record.level.name,
      levelValue = record.levelValue,
      message = messages,
      fileName = record.fileName,
      className = record.className,
      methodName = record.methodName,
      line = record.line,
      column = record.column,
      data = record.data.map {
        case (key, value) => value() match {
          case value: Value => key -> value
          case any => key -> str(any.toString)
        }
      },
      trace = traces,
      timeStamp = l,
      date = l.t.F,
      time = s"${l.t.T}.${l.t.L}${l.t.z}"
    )
    val json = r.toValue
    val jsonString = if (compact) {
      fabric.parse.JsonWriter.Compact(json)
    } else {
      fabric.parse.JsonWriter.Default(json)
    }
    writer.write(record, new TextOutput(jsonString), outputFormat)
  }

  private def throwable2Trace(throwable: Throwable): Trace = {
    val elements = throwable.getStackTrace.toList.map { e =>
      TraceElement(e.getClassName, e.getMethodName, e.getLineNumber)
    }
    Trace(throwable.getLocalizedMessage, elements, Option(throwable.getCause).map(throwable2Trace))
  }
}

case class Record(level: String,
                  levelValue: Double,
                  message: Value,
                  fileName: String,
                  className: String,
                  methodName: Option[String],
                  line: Option[Int],
                  column: Option[Int],
                  data: Map[String, Value],
                  trace: Value,
                  timeStamp: Long,
                  date: String,
                  time: String)

object Record {
  implicit val mapRW: ReaderWriter[Map[String, Value]] = ReaderWriter[Map[String, Value]](t => t, _.asObj.value)
  implicit val rw: ReaderWriter[Record] = ccRW
}

case class Trace(message: String, elements: List[TraceElement], cause: Option[Trace])

object Trace {
  implicit val rw: ReaderWriter[Trace] = ccRW
}

case class TraceElement(`class`: String, method: String, line: Int)

object TraceElement {
  implicit val rw: ReaderWriter[TraceElement] = ccRW
}