package com.neighborhood.aka.laplace.estuary.core.lifecycle.prototype

import com.neighborhood.aka.laplace.estuary.core.lifecycle.worker.PositionRecorder
import com.neighborhood.aka.laplace.estuary.core.offset.ComparableOffset
import com.neighborhood.aka.laplace.estuary.mysql.lifecycle.BinlogPositionInfo
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
  * Created by john_liu on 2019/1/10.
  * 这个是处理Offset的Actor，用于SavePoint保存
  */
trait SourceDataPositionRecorder[A <: ComparableOffset[A]] extends ActorPrototype with PositionRecorder {
  private val queneMaxSize = 10
  private lazy val quene: mutable.Queue[A] = new mutable.Queue[A]()

  protected def error: Receive = {
    case _ =>
  }

  /**
    * @note 阻塞操作
    * @return
    */
  def startPosition: Option[A]

  def destination: String = this.syncTaskId

  private var latestOffset: Option[A] = None
  private var lastSavedOffset: Option[A] = None
  private var scheduledSavedOffset: Option[A] = None
  private var schedulingSavedOffset: Option[A] = None

  def updateOffset(offset: A): Unit
  = {
    latestOffset = Option(offset)
    setProfilingContent
  }

  def saveOffset: Unit = {
    log.info(s"start saveOffset,id:$syncTaskId")
    scheduledSavedOffset.map(saveOffsetInternal(_))
    scheduledSavedOffset.map(updateQuene(_))
    scheduledSavedOffset = schedulingSavedOffset
    schedulingSavedOffset = lastSavedOffset
    lastSavedOffset = latestOffset
  }

  def saveOffsetWhenError(e: Throwable, offset: Option[A]): Unit = {
    val oldest: Option[A] = quene.headOption
    offset.flatMap(getMatchOffset(_)).fold {
      log.warning(s"this can be really dangerous,cause cannot find a suitable offset to save when error, considering of loss of data plz,id:$syncTaskId")
      oldest.map(saveOffsetInternal(_)) //把最老的保存一下
      if (oldest.isDefined) log.warning(s"try to save the oldest offset instead,but still can not guarantee no data is loss,id:$syncTaskId ")
    }(saveOffsetInternal(_))

    context.become(error)
    throw new RuntimeException(s"some thing wrong,e:$e,message:${e.getMessage},id:$syncTaskId")
  }

  /**
    * 这是一个特殊情况
    * 会被特殊的事件触发
    * 效果是清除所有的缓存offset,保留latest offset
    */
  def saveLatestOffset: Unit = {
    log.warning(s"start to save latest offset,id:$syncTaskId")
    lastSavedOffset = latestOffset
    schedulingSavedOffset = latestOffset
    scheduledSavedOffset = latestOffset
    quene.clear() //将队列清空
    saveOffset
  }

  /**
    * 确保当前保存的position比curr 小
    *
    * @param curr 比较的offset
    */
  def ensureOffset(curr: A): Unit = {
    log.warning(s"start to ensure offset,id:$syncTaskId")
    val matchOffset = getMatchOffset(curr).getOrElse(throw new RuntimeException(s"cannot find match offset when ensure offset,id:$syncTaskId"))
    scheduledSavedOffset = scheduledSavedOffset.map(offset => offset.compare(matchOffset, true))
    log.warning(s"ensure offset,now is ${scheduledSavedOffset},id:$syncTaskId")
  }

  protected def saveOffsetInternal(offset: A): Unit

  protected def getSaveOffset: Option[A]

  protected def getLatestOffset: Option[A] = latestOffset

  protected def getLastOffset: Option[A] = lastSavedOffset

  protected def getScheduledSavedOffset: Option[A] = scheduledSavedOffset

  protected def getSchedulingSavedOffset: Option[A] = schedulingSavedOffset

  protected def setProfilingContent: Unit

  private def updateQuene(offset: A): Unit = {
    quene.enqueue(offset)
    if (quene.size > queneMaxSize) quene.dequeue()
  }

  private def getMatchOffset(offset: A): Option[A] = quene.reverse.dequeueFirst(offset.compare(_))

  /**
    * 在preStart里面初始化，避免引用逃逸
    */
  override def preStart(): Unit = {
    super.preStart()
    log.info(s"positionRecorder process preStart,id:$syncTaskId")
    latestOffset = startPosition
    lastSavedOffset = startPosition
    scheduledSavedOffset = startPosition
    schedulingSavedOffset = startPosition
    startPosition.map(quene.enqueue(_))
  }
}
