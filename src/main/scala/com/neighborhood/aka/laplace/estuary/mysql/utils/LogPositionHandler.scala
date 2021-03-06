package com.neighborhood.aka.laplace.estuary.mysql.utils

import java.io.IOException
import java.net.InetSocketAddress

import com.alibaba.otter.canal.parse.exception.CanalParseException
import com.alibaba.otter.canal.parse.inbound.mysql.dbsync.DirectLogFetcher
import com.alibaba.otter.canal.parse.index.ZooKeeperLogPositionManager
import com.alibaba.otter.canal.protocol.CanalEntry
import com.alibaba.otter.canal.protocol.position.{EntryPosition, LogIdentity, LogPosition}
import com.neighborhood.aka.laplace.estuary.core.source.DataSourceConnection
import com.neighborhood.aka.laplace.estuary.core.task.PositionHandler
import com.neighborhood.aka.laplace.estuary.core.util.JavaCommonUtil
import com.neighborhood.aka.laplace.estuary.mysql.source.MysqlConnection
import com.taobao.tddl.dbsync.binlog.{LogContext, LogDecoder}
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.util.CollectionUtils

import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.util.Try

/**
  * Created by john_liu on 2018/2/4.
  */
class LogPositionHandler(

                          val manager: ZooKeeperLogPositionManager,
                          val master: Option[EntryPosition] = None,
                          val standby: Option[EntryPosition] = None,
                          val slaveId: Long = -1L,
                          val destination: String = "",
                          val address: InetSocketAddress,
                          implicit val binlogParser: MysqlBinlogParser

                        ) extends PositionHandler[EntryPosition] {
  val logger = LoggerFactory.getLogger(classOf[LogPositionHandler])
  private val logPositionManager = manager
  manager.start()

  def isStart:Boolean = manager.isStart
  def close():Unit = manager.stop()
  /**
    *
    * @param destination 其实就是taskid 作为zk记录的标识
    *
    * @return 保存的zk
    */
  def getLatestIndexBy(destination: String = this.destination): LogPosition = manager.getLatestIndexBy(destination)

  /**
    * @param destination 其实就是taskid 作为zk记录的标识
    * @param logPosition 待被记录的log
    *                    记录 log 到 zk 中
    */
  def persistLogPosition(destination: String, logPosition: LogPosition): Unit = {
    manager.persistLogPosition(destination, logPosition)
    logger.info(s"binlog Position Saved id:$destination")
  }

  /**
    * @param destination 其实就是taskid 作为zk记录的标识
    * @param journalName binlog文件的JournalName
    * @param offset      binlog的offset
    *                    记录 log 到 zk 中
    */
  def persistLogPosition(destination: String, journalName: String, offset: Long, ts: Long = 0l): Unit = {
    val logPosition = buildLastPosition(journalName, offset, ts = ts)
    //    if(destination == null ||destination.trim == ""){
    //      ""
    //    }
    //    if(journalName == null || destination.trim == ""){
    //      ""
    //    }
    manager.persistLogPosition(destination, logPosition)
    logger.info(s"binlog Position Saved id:$destination")
  }

  override def persistLogPosition(destination: String, entryPosition: EntryPosition): Unit = {
    lazy val offset: Long = entryPosition.getPosition
    lazy val ts: Long = Try(entryPosition.getTimestamp.toLong).getOrElse(0l)
    lazy val journalName: String = entryPosition.getJournalName
    lazy val theLogPosition = buildLastPosition(journalName, offset, ts = ts)

    manager.persistLogPosition(destination, theLogPosition)
    logger.info(s"binlog Position Saved id:$destination")
  }


  /**
    * @param connection mysqlConnection
    *                   获取开始的position
    */
  override def findStartPosition(connection: DataSourceConnection): EntryPosition = {
    if (!connection.isConnected) connection.connect()
    val re = findStartPositionInternal(connection.asInstanceOf[MysqlConnection])
    connection.disconnect()
    re
  }

  /**
    * @param conn mysqlConnection
    *             主要是应对@TableIdNotFoundException 寻找事务开始的头
    */
  def findStartPositionWithinTransaction(conn: MysqlConnection): EntryPosition = {
    val connection = conn.fork
    if (!connection.isConnected) connection.connect()
    val startPosition = findStartPositionInternal(connection)
    val preTransactionStartPosition = findTransactionBeginPosition(connection, startPosition)
    if (!preTransactionStartPosition.equals(startPosition.getPosition)) {
      startPosition.setPosition(preTransactionStartPosition)
    }
    connection.disconnect()
    startPosition
  }

  /**
    *
    * @param connection mysqlConnection
    *                   寻找逻辑
    *                   首先先到zookeeper里寻址，以taskId作为唯一标识
    *                   否则检查是是否有传入的entryPosition并是否有效
    *                   否则默认读取最后一个binlog
    * @todo 主备切换
    */
  def findStartPositionInternal(connection: MysqlConnection): EntryPosition = {
    //第一步试图从zookeeper中拿到binlog position
    val logPositionFromZookeeper = Option(this.getlatestIndexBy(destination))

    def findBinlogPositionIfZkisEmptyOrInvaild = {
      //zookeeper未能拿到
      logger.debug(s"do not find position in Zk id:$destination")
      //todo 主备切换
      //看看是否传入entryPosition
      master
        .fold {
          //未传入logPostion
          //读取最后位置

          findEndPosition(connection)
        } {
          //传入了logPosition的话
          thePosition =>

            lazy val journalName = thePosition.getJournalName
            lazy val binlogPosition = thePosition.getPosition
            lazy val timeStamp = thePosition.getTimestamp
            //jouralName是否定义
            val journalNameIsDefined = !JavaCommonUtil.isEmpty(journalName)
            //时间戳是否定义
            val timeStampIsDefined = (Option(timeStamp).isDefined && timeStamp > 0L)
            //positionOffset是否定义
            val positionOffsetIsDefined = (Option(binlogPosition).isDefined && binlogPosition >= 4L)
            (journalNameIsDefined, positionOffsetIsDefined, timeStampIsDefined) match {
              case (true, true, true) => thePosition
              case (true, true, false) => thePosition
              case (true, false, true) => findByStartTimeStamp(connection, timeStamp)
              case (true, false, false) => findEndPosition(connection)
              case (false, true, true) => findByStartTimeStamp(connection, timeStamp)
              case (false, true, false) => findEndPosition(connection)
              case (false, false, true) => findByStartTimeStamp(connection, timeStamp)
              case (false, false, false) => findEndPosition(connection)
            }
        }
    }

    logPositionFromZookeeper
      .fold {

        findBinlogPositionIfZkisEmptyOrInvaild
      } {
        //如果传了
        theLogPosition =>
          //binlog 被移除的话
          if (binlogIsRemoved(connection, theLogPosition.getJournalName)) findBinlogPositionIfZkisEmptyOrInvaild else {
            logger.info(s"find logPosition by zk, id:$destination position:${
              theLogPosition.getJournalName
            }:${theLogPosition.getPosition}")
            theLogPosition
          }
      }

  }

  /**
    * 查看binlog在mysql中是否被移除
    * 利用`show binlog event in '特定binlog'`
    * * @param mysqlConnection
    *
    * @param journalName
    * @return
    * @todo 有问题
    */
  private def binlogIsRemoved(mysqlConnection: MysqlConnection, journalName: String): Boolean = {
    Try {
      import scala.concurrent.duration._
      val fields = Await
        .result(Future(mysqlConnection.query(s"show binlog events in '$journalName' limit 1").getFieldValues)(scala.concurrent.ExecutionContext.Implicits.global), 3 seconds)
      CollectionUtils.isEmpty(fields)
    }.getOrElse {
      logger.warn(s"error when ensure binlog:$journalName exists or not,REGARDED AS NO ZK logPosition!")
      true
    } //throw new Exception("error when ensure binlog exists or not"))
  }

  /**
    * 利用`show master status`语句查找当前最新binlog
    *
    * @todo 写的不好，要重写
    *
    */
  def findEndPosition(mysqlConnection: MysqlConnection): EntryPosition = {
    logger.debug(s"start find endPosition id:$destination")

    lazy val re = try {
      val jdbcConnection = mysqlConnection.toJdbcConnecton
      val fields = Try(jdbcConnection.selectSql("show master status")(0)).toOption
      if (fields.isEmpty || fields.get.isEmpty) throw new CanalParseException("command : 'show master status' has an error! pls check. you need (at least one of) the SUPER,REPLICATION CLIENT privilege(s) for this operation")
      lazy val endPosition = fields.map {
        fieldList =>
          lazy val list = fieldList.map(_._2.toString).toList
          new EntryPosition(list(0), list(1).toLong)
      }

      endPosition.get
    } catch {
      case e: IOException => {
        throw new CanalParseException(" command : 'show master status' has an error!", e);
      }
    }

    logger.debug(s"find end Position ${re.getJournalName}:${re.getPosition},id:$destination")
    re
  }

  /**
    * 寻找事务开始的position
    * 这个方法也做了scala风格的修改
    * 1.首先确认 当前给定的position是否是事务头/尾,如果是直接使用
    * 2.否则从当前binlog头开始寻找，找到事务头
    *
    * @param mysqlConnection
    * @param entryPosition
    * @return position
    */
  def findTransactionBeginPosition(mysqlConnection: MysqlConnection, entryPosition: EntryPosition): Long = // 尝试找到一个合适的位置
  {
    def prepareConnection(position: Long) = {
      mysqlConnection.reconnect()
      MysqlConnection.seek(entryPosition.getJournalName, position)(mysqlConnection)
    }

    prepareConnection(entryPosition.getPosition)
    if (mysqlConnection.fetch4Seek.getEntryType == CanalEntry.EntryType.TRANSACTIONBEGIN || mysqlConnection.fetch4Seek.getEntryType == CanalEntry.EntryType.TRANSACTIONEND) entryPosition.getPosition
    else {
      prepareConnection(4L)

      @tailrec
      def loopFind: Long = {
        val theEntry = mysqlConnection.fetch4Seek
        if (theEntry.getHeader.getLogfileOffset > entryPosition.getPosition) {
          throw new CanalParseException("the current entry is bigger than last when find Transaction Begin Position")
        }
        if (theEntry.getEntryType == CanalEntry.EntryType.TRANSACTIONBEGIN)
          theEntry.getHeader.getLogfileOffset else loopFind
      }

      loopFind
    }

  }

  /**
    * @param entry   Canal Entry
    * @param address mysql地址
    *                从entry 构建成 LogPosition
    */

  def buildLastPositionByEntry(entry: CanalEntry.Entry, address: InetSocketAddress = this.address) = {
    val logPosition = new LogPosition
    val position = new EntryPosition
    position.setJournalName(entry.getHeader.getLogfileName)
    position.setPosition(entry.getHeader.getLogfileOffset)
    position.setTimestamp(entry.getHeader.getExecuteTime)
    position.setServerId(entry.getHeader.getServerId)
    logPosition.setPostion(position)
    val identity = new LogIdentity(address, -1L)
    logPosition.setIdentity(identity)
    logPosition
  }

  /**
    * @param journalName binlog文件名
    * @param offset      文件偏移量
    * @param address     mysql地址
    *                    从entry 构建成 LogPosition
    */

  def buildLastPosition(journalName: String, offset: Long, address: InetSocketAddress = this.address, slaveId: Long = this.slaveId, ts: Long = 0l) = {
    val logPosition = new LogPosition
    val position = new EntryPosition
    position.setJournalName(journalName)
    position.setPosition(offset)
    position.setTimestamp(ts)
    logPosition.setPostion(position)
    val identity = new LogIdentity(address, slaveId)
    logPosition.setIdentity(identity)
    logPosition
  }

  /**
    * 基于时间戳查找
    *
    * @param mysqlConnection
    * @param startTimeStamp
    * @return
    */
  def findByStartTimeStamp(mysqlConnection: MysqlConnection, startTimeStamp: Long): EntryPosition = {
    val endPosition = findEndPosition(mysqlConnection)
    val startPosition = findfirstPosition(mysqlConnection)
    val maxBinlogFileName = endPosition.getJournalName
    val minBinlogFileName = startPosition.getJournalName

    @tailrec
    def loopSearch(currentSearchBinlogFile: String = maxBinlogFileName): EntryPosition = {
      val entryPosition = findAsPerTimestampInSpecificLogFile(mysqlConnection, startTimeStamp, endPosition, currentSearchBinlogFile)
      (Option(entryPosition)) match {
        case None => {
          //为true表示已经遍历到头
          StringUtils.equalsIgnoreCase(minBinlogFileName, currentSearchBinlogFile) match {
            case true => {
              logger.warn(s"this timestamp:$startTimeStamp is eariler than minBinlogFileName:$minBinlogFileName,take minBinlogFileName as the matched binlog  ")
              startPosition
            }
            case false => {
              val binlogSeqNum = currentSearchBinlogFile.substring(currentSearchBinlogFile.indexOf(".") + 1).toInt
              if (binlogSeqNum <= 1) {
                logger.info(s"currentSearchBinlogFile:$currentSearchBinlogFile does not match,continue search")
                null
              } else {
                val nextBinlogSeqNum = binlogSeqNum - 1
                val binlogFileNamePrefix = currentSearchBinlogFile.substring(0, currentSearchBinlogFile.indexOf(".") + 1)
                val binlogFileNameSuffix = {
                  nextBinlogSeqNum match {
                    case x if (x < 1) => throw new Exception("binlog name cannot lt 1!!")
                    case x if (x < 10) => s"00000$x"
                    case x if (x < 100) => s"0000$x"
                    case x if (x < 1000) => s"000$x"
                    case x if (x < 10000) => s"00$x"
                    case x if (x < 100000) => s"0$x"
                    case x => s"$x" //todo 找贺老师确认

                  }
                }
                val nextSearchBinlogFile = binlogFileNamePrefix + binlogFileNameSuffix
                loopSearch(nextSearchBinlogFile)
              }
            }
          }
        }
        case Some(theEntryPosition) => {
          logger.info(s"find matched entry Position by timestamp:${theEntryPosition.getJournalName}:${theEntryPosition.getPosition}-${theEntryPosition.getTimestamp}")
          theEntryPosition
        }
      }
    }

    loopSearch()
  }

  /**
    * 查询当前binlog位置，这个主要是用于寻址
    *
    * @param mysqlConnection
    * @return
    */
  private[estuary] def findfirstPosition(mysqlConnection: MysqlConnection): EntryPosition = {
    lazy val endPosition = try {
      lazy val fields = mysqlConnection.
        query("show binlog events limit 1")
        .getFieldValues
      new EntryPosition(fields.get(0), fields.get(1).toLong)
    } catch {
      case e: Exception => throw new CanalParseException("command : 'show master status' has an error!", e)
    }
    endPosition
  }

  /**
    * 注：canal原生的方法，这里进行了scala风格修改
    * 根据给定的时间戳，在指定的binlog中找到最接近于该时间戳(必须是小于时间戳)的一个事务起始位置。
    * 针对最后一个binlog会给定endPosition，避免无尽的查询
    *
    * @todo 换成fetch4seek function
    */
  private[estuary] def findAsPerTimestampInSpecificLogFile(mysqlConnection: MysqlConnection, startTimestamp: Long, endPosition: EntryPosition, searchBinlogFile: String): EntryPosition = {
    logger.info(s" start find entry by timestamp in $searchBinlogFile,$startTimestamp")
    //重启一下
    Try(mysqlConnection.reconnect)

    // 开始遍历文件
    MysqlConnection.seek(searchBinlogFile, 4L)(mysqlConnection)
    val fetcher: DirectLogFetcher = mysqlConnection.fetcher4Seek
    val decoder: LogDecoder = mysqlConnection.decoder4Seek
    val logContext: LogContext = mysqlConnection.logContext4Seek
    val re = loopFetchAndFindEntry(fetcher, decoder, logContext)(startTimestamp, endPosition)
    re


  }

  /**
    * 在寻找binlog位置时用的方法
    *
    * @param fetcher 拉取binlog文件的DirectFetcher
    * @param decoder
    * @todo test
    */
  @tailrec
  final private[estuary] def loopFetchAndFindEntry(fetcher: DirectLogFetcher, decoder: LogDecoder, logContext: LogContext)(startTimestamp: Long = 0L, endPosition: EntryPosition = null): EntryPosition = {
    if (fetcher.fetch()) {
      val event = decoder.decode(fetcher, logContext)
      val entry = try {
        binlogParser.parse(Option(event))
      } catch {
        case e: CanalParseException => {
          // log.warning(s"table has been removed")
          None
        }
      }

      /**
        * 寻找到Entry并且判断这个Entry进行处理
        * 如果比最后的时间戳还晚 -> 返回1 -> null
        * 如果是事务头或者事务尾 -> 返回2 -> 以这个entry构建
        * 如果不属于上述几种情况 -> 返回3 -> 继续loopFetch
        *
        * @todo test
        */
      def findAndJudgeEntry(entry: CanalEntry.Entry): Int = {
        val logfilename = entry.getHeader.getLogfileName
        val logfileoffset = entry.getHeader.getLogfileOffset
        val logposTimestamp = entry.getHeader.getExecuteTime
        val entryType = entry.getEntryType

        //比最晚的都晚
        def lateThanLatest: Boolean = if (endPosition != null) (StringUtils.equals(endPosition.getJournalName, logfilename) && endPosition.getPosition <= (logfileoffset + event.getEventLen)) else false

        //比最早的都早
        def earlierThanEarliest: Boolean = if (startTimestamp != 0) logposTimestamp <= startTimestamp else false


        def outOfTimeRequirement = (lateThanLatest)

        def isquilified = earlierThanEarliest

        //进行判断
        if (!isquilified) 1 else entryType match {
          case CanalEntry.EntryType.TRANSACTIONEND => logger.info(s"find Transaction end, id $destination"); 2
          case CanalEntry.EntryType.TRANSACTIONBEGIN => logger.info(s"find Transaction begin, id $destination"); 2
          case _ => 3
        }
      }

      if (entry.isEmpty) loopFetchAndFindEntry(fetcher, decoder, logContext)(startTimestamp, endPosition) else {
        findAndJudgeEntry(entry.get) match {
          case 1 => null
          case 2 => new EntryPosition(entry.get.getHeader.getLogfileName, entry.get.getHeader.getLogfileOffset, entry.get.getHeader.getExecuteTime)
          case _ => loopFetchAndFindEntry(fetcher, decoder, logContext)(startTimestamp, endPosition)
        }
      }
    } else throw new Exception("unexcepted end when find And Judge Entry ")
  }

  override def getlatestIndexBy(destination: String): EntryPosition = Option(manager.getLatestIndexBy(destination)).map(_.getPostion).getOrElse(null)
}




