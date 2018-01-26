package com.starbucks.bids

import java.io.File
import java.net.URI
import java.nio.file.Files

import com.microsoft.azure.eventprocessorhost.{EventProcessorHost, EventProcessorOptions}
import com.microsoft.azure.keyvault.extensions.{RsaKey, SymmetricKey}
import com.microsoft.azure.storage.{CloudStorageAccount, OperationContext}
import com.microsoft.azure.storage.blob._
import com.starbucks.bids.Blob.{BlobConnectionInfo, BlobManager}
import com.starbucks.bids.EventHub.{EventHubConnectionInfo, EventHubErrorNotificationHandler, EventHubManager, EventProcessorFactory}
import com.starbucks.bids.KeyVault.{KeyVaultConnectionInfo, KeyVaultManager}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionException
import scala.util.{Failure, Success}

/**
  * Created by depatel on 1/2/18.
  */
object Main {

  val logger = LoggerFactory.getLogger("com.starbucks.bids.Main")

  def main(args: Array[String]): Unit = {

    val conf = new AppConfig(args)
    logger.info(conf.summary)

    // Get blob connection details.
    val blobConnectionInfo = new BlobConnectionInfo(
      conf.blobStoreAccountName(),
      conf.blobStoreAccountKey(),
      conf.blobStoreSourceContainerName(),
      conf.blobStoreDestContainerName()
    )

    val keyVaultConnectionInfo = new KeyVaultConnectionInfo(conf.spnClientId(), conf.spnClientKey())

    val keyVaultKey = KeyVaultManager.getKey(keyVaultConnectionInfo, conf.keyVaultResourceId())


    // TODO : Get the local keyResolver for Blob Client-Side Encryption. NEEDS TO BE TESTED!
    val keyBytes = Files.readAllBytes(new File(conf.encryptionKeyPath()).toPath)
    val localKey = new SymmetricKey("vendor", keyBytes)

    BlobManager.getBlobDirectoryReference(blobConnectionInfo, conf.blobDirectory()) match {
      case Success(blobDirectory) => {
        blobDirectory.listBlobs.forEach(blobFile => {
          println(blobFile.getStorageUri)
          if (!blobFile.isInstanceOf[CloudBlobDirectory]) {
            val file = blobFile.getUri.getPath.drop(1).split("\\/").filter(word => !(word.contains(conf.blobStoreSourceContainerName()))).mkString("/")
            println(s"For file: ${file}")
            BlobManager.getBlockBlobReference(blobConnectionInfo, conf.blobStoreSourceContainerName(), file) match {
              case Failure(exception) =>
                println(s"Exception in getting blob reference!")
                println(exception)

              case Success(blobReference: CloudBlockBlob) => {
                val blobEncryptionPolicy = new BlobEncryptionPolicy(keyVaultKey.get, null)
                val blobRequestOptions = new BlobRequestOptions()
                val operationContext = new OperationContext()
                blobRequestOptions.setEncryptionPolicy(blobEncryptionPolicy)
                blobRequestOptions.setConcurrentRequestCount(10)
                operationContext.setLoggingEnabled(true)
                blobReference.downloadAttributes()
                // Now we can download the blob to an ByteArrayInputStream.
                val blobOutputStream = new BlobUploadOutputStream()
                val testStream = blobReference.openInputStream(null, blobRequestOptions, operationContext)
//                blobReference.download(blobOutputStream, null, blobRequestOptions, null)
                // Now create another blob item in the destination container/directory and then upload the previous InputArrayStream.
                // TODO: Get output destination blob reference.
                BlobManager.getBlockBlobReference(blobConnectionInfo, conf.blobStoreDestContainerName(), file) match {
                  case Failure(exception) => println(exception); Nil
                  case Success(outBlobReference) => {
                    val outBlobEncryptionPolicy = new BlobEncryptionPolicy(localKey, null)
                    val outBlobRequestOptions = new BlobRequestOptions()
                    outBlobRequestOptions.setConcurrentRequestCount(100)
                    outBlobRequestOptions.setEncryptionPolicy(outBlobEncryptionPolicy)
                    outBlobReference.setStreamWriteSizeInBytes(32000000)
                    val outStream = outBlobReference.openOutputStream(null, outBlobRequestOptions, operationContext)
//                    outBlobReference.upload(testStream, -1, null, outBlobRequestOptions, operationContext)
//                    outBlobReference.upload(blobOutputStream.toInputStream, -1, null, outBlobRequestOptions, operationContext)
                    blobReference.download(outStream, null, blobRequestOptions, operationContext)
                  }

                }

              }
            }
          }
        })
      }
      case Failure(exception) => {
        println(s"Unable to get blob directory reference for ${conf.blobStoreSourceContainerName}")
        println(s"Exception : ${exception}")
      }
    }

    // TODO: Download the files from destination container.
//    DecryptBlobFiles.decryptBlobContentsRecursively(conf.blobStoreSASUri(), conf.encryptionKeyPath(), conf.blobStoreDestContainerName())
    // Given only Blob Storage URI to folder and SASToken with read only permission.
    // Assuming that the encrypt/decrypt key has already been shared.
//    val sasUri = conf.blobStoreSASUri()
//    val cloudBlobContainer = new CloudBlobContainer(new URI(sasUri))
//
//    val decryptKeyBytes = Files.readAllBytes(new File(conf.encryptionKeyPath()).toPath)
//    val decryptKey = new SymmetricKey("vendor", keyBytes)
//
//    cloudBlobContainer.listBlobs().forEach( blob => {
//      val blobDecryptionPolicy = new BlobEncryptionPolicy(decryptKey, null)
//      val blobRequestOptions = new BlobRequestOptions()
//      blobRequestOptions.setConcurrentRequestCount(10)
//      blobRequestOptions.setEncryptionPolicy(blobDecryptionPolicy)
//      val operationContext = new OperationContext()
//      val blobFix = "/sb/green/outgoing/AmperityLyticsAndRedPointExport/POSData/Headerfile.txt"
//      val blobName = blob.getUri.getPath.drop(1).split("\\/").filter(word => !(word.contains(conf.blobStoreDestContainerName()))).mkString("/")+blobFix
//      println(blob.getUri.getPath.drop(1).split("\\/").filter(word => !(word.contains(conf.blobStoreDestContainerName()))).mkString("/"))
//      println(blobName)
//      val blobReference = cloudBlobContainer.getBlockBlobReference(blobName)
//      println(s"PRINTING THE FILE : ${blobName}")
//      println(blobReference.downloadText("UTF-8", null, blobRequestOptions, operationContext))
//    })
    System.exit(0)
  }
}
