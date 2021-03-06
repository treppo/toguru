package toguru.toggles

import akka.persistence.journal._
import akka.serialization.SerializerWithStringManifest
import com.trueaccord.scalapb.GeneratedMessage
import toguru.toggles.events._
import toguru.toggles.snapshots.{DeletedToggleSnapshot, ExistingToggleSnapshot}

/**
  * Marker trait for toggle events.
  */
trait ToggleEvent extends GeneratedMessage {
  def meta: Option[Metadata]
}

trait DeprecatedToggleEvent extends ToggleEvent

/**
  * Marker trait for toggle snapshots.
  */
trait ToggleSnapshot extends GeneratedMessage {
}

/**
  * Serializer for toggle events.
  */
class ToggleEventProtoBufSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 4001

  final val CreatedManifest              = classOf[ToggleCreated].getSimpleName
  final val UpdatedManifest              = classOf[ToggleUpdated].getSimpleName
  final val DeletedManifest              = classOf[ToggleDeleted].getSimpleName
  final val ActivationCreatedManifest    = classOf[ActivationCreated].getSimpleName
  final val ActivationUpdatedManifest    = classOf[ActivationUpdated].getSimpleName
  final val ActivationDeletedManifest    = classOf[ActivationDeleted].getSimpleName

  // deprecated events
  final val GlobalRolloutCreateManifest  = classOf[GlobalRolloutCreated].getSimpleName
  final val GlobalRolloutUpdatedManifest = classOf[GlobalRolloutUpdated].getSimpleName
  final val GlobalRolloutDeletedManifest = classOf[GlobalRolloutDeleted].getSimpleName

  override def manifest(o: AnyRef): String = o.getClass.getSimpleName

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case CreatedManifest              => ToggleCreated.parseFrom(bytes)
    case UpdatedManifest              => ToggleUpdated.parseFrom(bytes)
    case DeletedManifest              => ToggleDeleted.parseFrom(bytes)
    case ActivationCreatedManifest    => ActivationCreated.parseFrom(bytes)
    case ActivationUpdatedManifest    => ActivationUpdated.parseFrom(bytes)
    case ActivationDeletedManifest    => ActivationDeleted.parseFrom(bytes)
    case GlobalRolloutCreateManifest  => toActivation(GlobalRolloutCreated.parseFrom(bytes))
    case GlobalRolloutUpdatedManifest => toActivation(GlobalRolloutUpdated.parseFrom(bytes))
    case GlobalRolloutDeletedManifest => toActivation(GlobalRolloutDeleted.parseFrom(bytes))
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: ToggleEvent => e.toByteArray
  }

  def asRollout(percentage: Int) = Some(Rollout(percentage))

  def toActivation(e: GlobalRolloutCreated) = ActivationCreated(rollout = asRollout(e.percentage), meta = e.meta)
  def toActivation(e: GlobalRolloutUpdated) = ActivationUpdated(rollout = asRollout(e.percentage), meta = e.meta)
  def toActivation(e: GlobalRolloutDeleted) = ActivationDeleted(meta = e.meta)
}

/**
  * Tagging write adapter for toggles.
  * Required for persistent queries by tag.
  */
class ToggleEventTagging extends WriteEventAdapter {
  override def manifest(event: Any): String = ""

  def withTag(event: Any, tags: String*) = Tagged(event, tags.to[Set])

  override def toJournal(event: Any): Any = event match {
    case _: ToggleEvent          => withTag(event, "toggle")
    case _                       => event
  }
}

/**
  * Serializer for toggle snapshots.
  */
class ToggleSnapshotProtoBufSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 5001

  override def manifest(o: AnyRef): String = o.getClass.getSimpleName

  final val ExistingToggleSnapshotManifest = classOf[ExistingToggleSnapshot].getSimpleName
  final val DeletedToggleSnapshotManifest  = classOf[DeletedToggleSnapshot].getSimpleName

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case ExistingToggleSnapshotManifest => ExistingToggleSnapshot.parseFrom(bytes)
    case DeletedToggleSnapshotManifest  => DeletedToggleSnapshot.parseFrom(bytes)
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case s: ToggleSnapshot => s.toByteArray
  }
}
