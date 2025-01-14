package io.aiven.guardian.kafka.restore

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Sink
import io.aiven.guardian.kafka.models.ReducedConsumerRecord
import io.aiven.guardian.kafka.restore.configs.Restore
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.concurrent.Future

import java.util.Base64

class KafkaProducer(
    configureProducer: Option[
      ProducerSettings[Array[Byte], Array[Byte]] => ProducerSettings[Array[Byte], Array[Byte]]
    ] = None
)(implicit system: ActorSystem, restoreConfig: Restore)
    extends KafkaProducerInterface {

  private[kafka] val producerSettings = {
    val base = ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)
    configureProducer
      .fold(base)(block => block(base))
  }

  override def getSink: Sink[ReducedConsumerRecord, Future[Done]] =
    Producer.plainSink(producerSettings).contramap[ReducedConsumerRecord] { reducedConsumerRecord =>
      val topic = restoreConfig.overrideTopics match {
        case Some(map) =>
          map.getOrElse(reducedConsumerRecord.topic, reducedConsumerRecord.topic)
        case None => reducedConsumerRecord.topic
      }
      new ProducerRecord[Array[Byte], Array[Byte]](
        topic,
        Base64.getDecoder.decode(reducedConsumerRecord.key),
        Base64.getDecoder.decode(reducedConsumerRecord.value)
      )
    }
}
