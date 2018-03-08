package com.starbucks.analytics

import java.io.{ File, FileReader }

import com.starbucks.analytics.adls.ADLSUploader
import com.starbucks.analytics.db.{ DBManager, Utilities }
import com.starbucks.analytics.parsercombinator.Parser
import org.slf4j.{ Logger, LoggerFactory }

import scala.collection.immutable.Iterable
import scala.collection.parallel.{ ForkJoinTaskSupport, ParMap }

/**
 * Represents the configuration required for the application
 *
 * @param file Absolute path of the file containing upload instruction
 */
case class Config(
  file:                File   = new File("."),
  username:            String = null,
  password:            String = null,
  hostPort:            String = null,
  databaseName:        String = null,
  schemaOwner:         String = null,
  adlsStoreName:       String = null,
  adlsRootDir:         String = null,
  logicalDatabase:     String = null,
  predicateColumnName: String = null,
  authEndPoint:        String = null,
  adlsSubDir:          String = null,
  srcDomainName:       String = null,
  srcSubDomainName:    String = null,
  targetEnv:           String = null)
/**
 * Entry point for the application
 * Orchestrator
 */
object App extends App {
  val rootLogger: Logger = LoggerFactory.getLogger(
    org.slf4j.Logger.ROOT_LOGGER_NAME
  )

  // Parse the command line arguments
  // Exit if there is a problem parsing the command line arguments
  val config = parse(args)
  if (config.isEmpty) {
    System.exit(-1)
  }

  // Add the command line arguments to system properties.
  val oracle_jdbc = "jdbc:oracle:thin:@" + config.get.hostPort + "/" + config.get.databaseName
  sys.props.put("oracle_jdbc", oracle_jdbc)
  val configMap = config.get.getClass.getDeclaredFields.map(_.getName).zip(config.get.productIterator.to)
  configMap.foreach(pair => {
    sys.props.put(pair._1, pair._2.toString)
  })

  logStartupMessage(rootLogger, getApplicationName, config.get)

  /***************************************************************************************/
  /*                              MAGIC HAPPENS HERE                                     */
  /***************************************************************************************/
  val reader = new FileReader(config.get.file)
  val parserResult = Parser.parse(reader)
  rootLogger.debug(
    s"""Lexical result of parsing ${config.get.file.getAbsolutePath}:
       |\t\t $parserResult
       """.stripMargin
  )
  /***************************************************************************************/
  /*                      USING PARSER RESULT UPLOAD TO AZURE                            */
  /***************************************************************************************/
  if (parserResult.isRight) {
    val dbConnectionInfo = parserResult.right.get._1
    val adlsConnectionInfo = parserResult.right.get._2
    val uploaderOptionsInfo = parserResult.right.get._3
    val sqlFileMap = parserResult.right.get._4

    // Cleanup
    if (uploaderOptionsInfo.deleteFilesInParent) {
      val parentPaths: List[String] = sqlFileMap.map(sqlFile => {
        if (sqlFile._1.isDefined) {
          val parentPath: String = sqlFile._2.get.substring(
            0,
            sqlFile._2.get.lastIndexOf("/")
          )
          Some(parentPath)
        } else {
          None
        }
      })
        .filter(x => x.isDefined)
        .map(x => x.get)
        .toList
        .distinct

      parentPaths.foreach(parentPath => {
        rootLogger.info(
          s"""Cleaning up parent folder $parentPath """.stripMargin
        )

        ADLSUploader.deleteParentFolder(
          adlsConnectionInfo.clientId,
          adlsConnectionInfo.clientKey,
          adlsConnectionInfo.authenticationTokenEndpoint,
          adlsConnectionInfo.accountFQDN,
          parentPath
        )
      })
    }

    // Setup parallelism
    val parallelSqlFileMap: ParMap[Option[(String, List[String])], Option[String]] = sqlFileMap.par
    parallelSqlFileMap.tasksupport = new ForkJoinTaskSupport(
      new scala.concurrent.forkjoin.ForkJoinPool(uploaderOptionsInfo.desiredParallelism)
    )
    // Uploading
    parallelSqlFileMap.foreach(sqlFile => {
      if (sqlFile._1.isDefined) {
        rootLogger.info(
          s"""Initializing transfer of SQL: ${sqlFile._1.get},
           | to: ${sqlFile._2.get}""".stripMargin
        )
        // Step 1. Initialize the uploader
        val uploader = ADLSUploader(
          adlsConnectionInfo.clientId,
          adlsConnectionInfo.clientKey,
          adlsConnectionInfo.authenticationTokenEndpoint,
          adlsConnectionInfo.accountFQDN,
          sqlFile._2.get,
          "755",
          uploaderOptionsInfo.desiredBufferSize
        )
        try {
          DBManager.withResultSetIterator[Unit, Array[Byte]](
            dbConnectionInfo,
            sqlFile._1.get._1,
            uploaderOptionsInfo.fetchSize, {
            resultSet =>
              Utilities.resultSetToByteArray(
                resultSet,
                sqlFile._1.get._2,
                uploaderOptionsInfo.separator,
                uploaderOptionsInfo.rowSeparator
              )
          }, {
            resultsIterator => resultsIterator.foreach(uploader.bufferedUpload)
          }
          )
        } finally {
          uploader.close()
        }
      }
    })
  }
  /***************************************************************************************/

  // Utility function to return the application name
  private def getApplicationName: String = new java.io.File(classOf[App]
    .getProtectionDomain
    .getCodeSource
    .getLocation
    .getPath)
    .getName

  /**
   * Parses the command line arguments using scopt
   *
   * @param args Command line arguments
   * @return A valid configuration object parsing was successful
   */
  def parse(args: Array[String]): Option[Config] = {
    val parser = new scopt.OptionParser[Config](getApplicationName) {
      override def showUsageOnError = true

      // Setup the parser
      head(getApplicationName)
      help("help") text "prints this usage text"
      opt[File]('f', "fileName")
        .valueName("<file name>")
        .required()
        .validate(f =>
          if (f.exists()) success
          else failure(s"The file ${f.getAbsolutePath} should exist."))
        .action { (x, c) => c.copy(file = x) }
        .text("File containing the uploader")
      opt[String]('d', "srcDomainName")
        .valueName("<src domain name>")
        .required()
        .action((x, c) => c.copy(srcDomainName = x))
        .text("Oracle source domain name")
      opt[String]('s', "srcSubDomainName")
        .valueName("<src subdomain name>")
        .required()
        .action((x, c) => c.copy(srcSubDomainName = x))
        .text("Oracle source subdomain name")
      opt[String]('e', "targetEnv")
        .valueName("ADLS target environment")
        .required()
        .action((x, c) => c.copy(targetEnv = x))
        .text("ADLS target environment")
      opt[String]('u', "username")
        .valueName("oracle username")
        .required()
        .action((x, c) => c.copy(username = x))
        .text("Oracle Username to connect")
      opt[String]('p', "password")
        .valueName("oracle password")
        .required()
        .action((x, c) => c.copy(password = x))
        .text("Oracle Username password")
      opt[String]('h', "hostPort")
        .valueName("oracle machine host:port")
        .required()
        .action((x, c) => c.copy(hostPort = x))
        .text("Oracle Machine Host:Port")
      opt[String]('b', "databaseName")
        .valueName("oracle database name")
        .required()
        .action((x, c) => c.copy(databaseName = x))
        .text("Oracle Database Name")
      opt[String]('a', "schemaOwner")
        .valueName("oracle database schema owner")
        .required()
        .action((x, c) => c.copy(schemaOwner = x))
        .text("Oracle database Schema Owner")
      opt[String]('c', "logicalDatabase")
        .valueName("oracle logical database/table name")
        .required()
        .action((x, c) => c.copy(logicalDatabase = x))
        .text("Oracle Logical Database Name")
      opt[String]('z', "predicateColumnName")
        .valueName("oracle table predicate column to filter")
        .required()
        .action((x, c) => c.copy(predicateColumnName = x))
        .text("Oracle Logical Database Name")
      opt[String]('o', "authEndPoint")
        .valueName("Azure SPN auth endpoint")
        .required()
        .action((x, c) => c.copy(authEndPoint = x))
        .text("Azure SPN Authentication End Point")
      opt[String]('t', "adlsSubDir")
        .valueName("ADLS target logical database")
        .required()
        .action((x, c) => c.copy(adlsSubDir = x))
        .text("ADLS Target Logical Database Name")
      opt[String]('r', "adlsRootDir")
        .valueName("ADLS Root Directory")
        .required()
        .action((x, c) => c.copy(adlsRootDir = x))
        .text("ADLS Data Storage Root Directory")
      opt[String]('j', "adlsStoreName")
        .valueName("ADLS Target Store Name")
        .required()
        .action((x, c) => c.copy(adlsStoreName = x))
        .text("ADLS Data Storage Target")
    }

    // Evaluate
    parser.parse(args, Config()) match {
      case Some(cfg) =>
        Some(cfg)
      case None =>
        None
    }
  }

  /**
   * Logs a startup message to the log
   *
   * @param logger          Logger used by the application
   * @param applicationName Name of the application
   * @param config          Data Transfer configuration
   */
  private def logStartupMessage(
    logger:          Logger,
    applicationName: String,
    config:          Config
  ): Unit = {
    logger.info(s"$applicationName starting with command line arguments: ")
    logger.info(s"\t Filename: ${config.file.getAbsolutePath}")
  }
}