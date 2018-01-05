package com.starbucks.bids.EventHub

import com.microsoft.azure.eventprocessorhost.{IEventProcessorFactory, PartitionContext}
import com.starbucks.bids.Blob.BlobConnectionInfo
import com.starbucks.bids.KeyVault.KeyVaultConnectionInfo

/**
  * Factory class to generate instances of Event Processor Class with constructor parameters.
  */
class EventProcessorFactory(blobConnectionInfo: BlobConnectionInfo, keyVaultConnectionInfo: KeyVaultConnectionInfo, keyVaultResourceUri: String, desiredParallelism: Int, vendorPubKey: String) extends IEventProcessorFactory[EventProcessor]{

  /**
    * Method to create Event Processor instances.
    * @param context
    * @return
    */
  override def createEventProcessor(context: PartitionContext): EventProcessor = {
    new EventProcessor(blobConnectionInfo, keyVaultConnectionInfo, keyVaultResourceUri, vendorPubKey, desiredParallelism)
  }
}
