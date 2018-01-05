package com.starbucks.bids.EventHub

import java.io.File
import java.lang
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

import com.google.gson.Gson
import com.microsoft.azure.eventhubs.EventData
import com.microsoft.azure.eventprocessorhost.{CloseReason, IEventProcessor, PartitionContext}
import com.microsoft.azure.keyvault.extensions.SymmetricKey
import com.microsoft.azure.storage.OperationContext
import com.microsoft.azure.storage.blob.{BlobEncryptionPolicy, BlobRequestOptions, CloudBlockBlob}
import com.starbucks.bids.Blob.{BlobConnectionInfo, BlobManager}
import com.starbucks.bids.BlobUploadOutputStream
import com.starbucks.bids.KeyVault.{KeyVaultConnectionInfo, KeyVaultManager}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.{ForkJoinTaskSupport, ParSeq}
import scala.util.{Failure, Success}

/**
  * Event Processor to process all the events received from Event Hub.
  */
class EventProcessor(blobConnectionInfo: BlobConnectionInfo, keyVaultConnectionInfo: KeyVaultConnectionInfo, keyVaultResourceUri: String, vendorPubKey: String, desiredParallelism: Int) extends IEventProcessor{

  private val logger = LoggerFactory.getLogger("com.starbucks.bids.Eventhub.EventProcessor")
  private var checkpointBatchingCount = 0
  private val cipher = Cipher.getInstance("AES")
  private val keyBytes = Files.readAllBytes(new File(vendorPubKey).toPath)
  private val localKey = new SymmetricKey("vendor", keyBytes)
  cipher.init(Cipher.DECRYPT_MODE, {
    val keyBytes = Files.readAllBytes(new File(vendorPubKey).toPath)
    new SecretKeySpec(keyBytes, "AES")
  })

  def toEvent(eventString: String) = new Gson().fromJson(eventString, classOf[Event])

  /**
    * Method invoked at the time of event processor registration.
    * @param context
    * @throws java.lang.Exception
    */
  @throws(classOf[Exception])
  override def onOpen(context: PartitionContext): Unit = {
    logger.info(s"Partition ${context.getPartitionId} is opening.")
  }

  /**
    * Method invoked when event processor un-registration.
    * @param context
    * @param reason
    * @throws java.lang.Exception
    */
  @throws(classOf[Exception])
  override def onClose(context: PartitionContext, reason: CloseReason): Unit = {
    logger.info(s"Partition ${context.getPartitionId} is closing.")
  }

  /**
    * Method to invoke when error is thrown from event processor host.
    * @param context
    * @param error
    */
  override def onError(context: PartitionContext, error: Throwable): Unit = {
    logger.info(s"Partition ${context.getPartitionId} on Error: ${error.toString}")
  }

  /**
    * Method to ingest and process the events received from the event hub in batches.
    * @param context
    * @param messages
    * @throws java.lang.Exception
    */
  @throws(classOf[Exception])
  override def onEvents(context: PartitionContext, messages: lang.Iterable[EventData]): Unit = {

      logger.info(s"Partition ${context.getPartitionId} got message batch")
      var lastEventData: EventData = null
      val messagesList = messages.asScala
      var eventsList: ListBuffer[Event] = ListBuffer[Event]()

    // Get the key vault resolver
//    val keyVaultResolver = new KeyVaultKeyResolver(KeyVaultManager.getKeyVaultKeyResolver(keyVaultConnectionInfo, keyVaultResourceUri))
    val keyVaultKey = KeyVaultManager.getKey(keyVaultConnectionInfo, keyVaultResourceUri)

      for (message: EventData <- messagesList) {
        logger.info(s"(Partition: ${context.getPartitionId}, offset: ${message.getSystemProperties.getOffset}," +
          s"SeqNum: ${message.getSystemProperties.getSequenceNumber}) : ${new String(message.getBytes, "UTF-8")}")
//        val msgString = new String(message.getBytes, "UTF-8")
        val msgString = new String( cipher.doFinal(message.getBytes), "UTF-8")
        logger.info(s"Received & Decrypted message: ${msgString} ")
        if (msgString.contains("uri") && msgString.contains("sharedAccessSignatureToken")) {
          val event: Event = toEvent(msgString)
          eventsList += event
        }
        lastEventData = message
      }
      val parallelListofEvents: ParSeq[Event] = eventsList.par
      parallelListofEvents.tasksupport = new ForkJoinTaskSupport(
        new scala.concurrent.forkjoin.ForkJoinPool(desiredParallelism)
      )

      parallelListofEvents.foreach(event => {
        logger.info(s"Start copying for file ${event.getUri}")
          val uris = event.getUri.split(";")
          val primaryUri = uris(0).split(" = ")(1).trim
          val secondaryUri = uris(1).split(" = ")(1).trim
          val sasUri = primaryUri.substring(1, primaryUri.length - 1) + "?" + event.getSASToken.trim
          logger.info("SAS URI for the blob is : " + sasUri)

          // Method to create and get Aure blob InputStream, blobName and blobSize.
          def getBlobStream(azureBlockBlob: CloudBlockBlob): (BlobUploadOutputStream, String, Long) = {
            val blobEncryptionPolicy = new BlobEncryptionPolicy(keyVaultKey.get, null)
            val blobRequestOptions = new BlobRequestOptions()
            val operationContext = new OperationContext()
            blobRequestOptions.setEncryptionPolicy(blobEncryptionPolicy)
            blobRequestOptions.setConcurrentRequestCount(100)
            operationContext.setLoggingEnabled(true)
            // get the blob file metadata.
            azureBlockBlob.downloadAttributes()
            val outStream = new BlobUploadOutputStream()
            azureBlockBlob.download(outStream, null, blobRequestOptions, null)
            (outStream, azureBlockBlob.getUri.getPath, azureBlockBlob.getProperties.getLength)
          }

          BlobManager.withSASUriBlobReference(sasUri, getBlobStream) match {
            case Failure(e) => {
              logger.error(s"Unable to get InputStream for ${sasUri}")
            }
            case Success(blobStreamData) => {
              val blobInputStream = blobStreamData._1
              val blobName = blobStreamData._2
              val blobSize = blobStreamData._3

              BlobManager.getBlockBlobReference(blobConnectionInfo, blobConnectionInfo.destContainer, blobName) match {
                case Failure(e) => println(e)
                case Success(outBlobReference) => {
                  val outBlobEncryptionPolicy = new BlobEncryptionPolicy(localKey, null)
                  val outBlobRequestOptions = new BlobRequestOptions()
                  val operationContext = new OperationContext()
                  outBlobRequestOptions.setEncryptionPolicy(outBlobEncryptionPolicy)
                  outBlobRequestOptions.setConcurrentRequestCount(10)
                  operationContext.setLoggingEnabled(true)
                  outBlobReference.upload(blobInputStream.toInputStream, -1, null, outBlobRequestOptions, operationContext)
                }
              }

//              val uploadResult = S3Manger.uploadToS3(blobInputStream, s3BucketName, s3FolderName, blobName, blobSize, s3Client)
//              if (uploadResult)
//                logger.info(s"${blobName} transfer to S3 ${s3BucketName} : SUCCESS")
//              else
//                logger.warn(s"${blobName} transfer to S3 ${s3BucketName} : FAILED.") //context.checkpoint(new EventData(event.toJson.getBytes()))
            }
          }
      })
      logger.info(s"Checkpointing last received event : ${lastEventData.toString}")
      context.checkpoint(lastEventData)
  }


}
